package org.backendbridge;

import com.sun.net.httpserver.HttpExchange;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Admin authentication + RBAC permission checks.
 */
public final class AdminAuth {

    private static final String COOKIE_NAME = "bb_session";
    private static final SecureRandom RNG = new SecureRandom();
    private static final Duration SESSION_TTL = Duration.ofHours(12);

    private final Db db;
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AdminAuth(Db db) {
        this.db = db;
    }

    public boolean isLoggedIn(HttpExchange ex) {
        return session(ex) != null;
    }

    public String loggedInUsername(HttpExchange ex) {
        Session s = session(ex);
        return s == null ? null : s.username;
    }

    public boolean hasPermission(HttpExchange ex, String permKey) {
        String u = loggedInUsername(ex);
        if (u == null) return false;

        String pk = permKey == null ? "" : permKey.trim();
        if (pk.isBlank()) return false;

        String sql =
                "SELECT 1 " +
                        "FROM web_users u " +
                        "JOIN web_role_permissions rp ON rp.role_id=u.role_id " +
                        "JOIN web_permissions p ON p.id=rp.perm_id " +
                        "WHERE u.username=? AND u.disabled_at IS NULL AND p.perm_key=? " +
                        "LIMIT 1";

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, u);
            ps.setString(2, pk);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            return false;
        }
    }

    public boolean login(HttpExchange ex, String username, String password) {
        String u = (username == null) ? "" : username.trim();
        if (u.isBlank()) return false;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT password_hash, disabled_at FROM web_users WHERE username=? LIMIT 1"
             )) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getTimestamp("disabled_at") != null) return false;
                if (!PasswordUtil.verifyPbkdf2(password, rs.getString("password_hash"))) return false;
            }

            String token = newToken();
            sessions.put(token, new Session(u, System.currentTimeMillis() + SESSION_TTL.toMillis()));
            setCookie(ex, COOKIE_NAME, token, true, (int) SESSION_TTL.toSeconds());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void logout(HttpExchange ex) {
        String token = cookie(ex, COOKIE_NAME);
        if (token != null) sessions.remove(token);
        setCookie(ex, COOKIE_NAME, "", true, 0);
    }

    private Session session(HttpExchange ex) {
        String token = cookie(ex, COOKIE_NAME);
        if (token == null) return null;
        Session s = sessions.get(token);
        if (s == null) return null;
        if (s.expiresAtMs < System.currentTimeMillis()) {
            sessions.remove(token);
            return null;
        }
        return s;
    }

    private static String newToken() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String cookie(HttpExchange ex, String name) {
        String raw = ex.getRequestHeaders().getFirst("Cookie");
        if (raw == null || raw.isBlank()) return null;

        for (String p : raw.split(";")) {
            String part = p.trim();
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = part.substring(0, idx).trim();
            String v = part.substring(idx + 1).trim();
            if (name.equals(k)) return v;
        }
        return null;
    }

    private static void setCookie(HttpExchange ex, String name, String value, boolean httpOnly, int maxAgeSeconds) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(name).append("=").append(value == null ? "" : value)
                .append("; Path=/")
                .append("; Max-Age=").append(Math.max(0, maxAgeSeconds))
                .append("; SameSite=Lax");
        if (httpOnly) sb.append("; HttpOnly");
        ex.getResponseHeaders().set("Set-Cookie", sb.toString());
    }

    private record Session(String username, long expiresAtMs) {}
}