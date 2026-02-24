package org.backendbridge.repo;

import org.backendbridge.Db;
import org.backendbridge.PasswordUtil;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Base64;

import static org.backendbridge.adminui.AdminUiUtil.esc;
import static org.backendbridge.adminui.AdminUiUtil.escAttr;
import static org.backendbridge.adminui.AdminUiUtil.minecraftAvatarUrl32;

/**
 * Manages web admin users (not Minecraft players).
 *
 * Password policy:
 * - raw passwords are accepted only at API boundary
 * - only PBKDF2 hashes are stored in DB (password_hash)
 */
public final class UsersRepository {

    private final Db db;

    public UsersRepository(Db db) {
        this.db = db;
    }

    /**
     * Ensures 'root' exists with admin role.
     * If rootPasswordHash is blank, a random password is generated and printed once.
     */
    public void ensureRootExists(String rootPasswordHash) throws Exception {
        try (Connection c = db.getConnection()) {
            if (userExists(c, "root")) return;

            String usedHash = rootPasswordHash;
            if (usedHash == null || usedHash.isBlank()) {
                String plain = generateRootPassword();
                usedHash = PasswordUtil.hashPbkdf2(plain);

                System.out.println("[BackendBridgeService] Created protected 'root' user.");
                System.out.println("[BackendBridgeService] Root password (save it now): " + plain);
            }

            long adminRoleId = roleIdByKey(c, "admin");
            if (adminRoleId <= 0) throw new IllegalStateException("admin role missing");

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO web_users(username, password_hash, role_id, is_protected) VALUES(?, ?, ?, 1)"
            )) {
                ps.setString(1, "root");
                ps.setString(2, usedHash);
                ps.setLong(3, adminRoleId);
                ps.executeUpdate();
            }
        }
    }

    public String listUsersHtmlRows() throws Exception {
        String sql =
                "SELECT u.id, u.username, u.is_protected, u.disabled_at, r.display_name AS role_name " +
                        "FROM web_users u " +
                        "JOIN web_roles r ON r.id = u.role_id " +
                        "ORDER BY u.is_protected DESC, u.username ASC " +
                        "LIMIT 500";

        StringBuilder sb = new StringBuilder(70_000);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                long id = rs.getLong("id");
                String username = rs.getString("username");
                boolean prot = rs.getInt("is_protected") == 1;

                String role = rs.getString("role_name");
                if (role == null || role.isBlank()) role = "—";

                boolean active = rs.getTimestamp("disabled_at") == null;

                String avatar = minecraftAvatarUrl32(username);
                String status = active
                        ? "<span class='pill pill-ok'>Active</span>"
                        : "<span class='pill pill-warn'>Disabled</span>";

                sb.append("<tr>")
                        .append("<td>")
                        .append("<div class='bb-userCell'>")
                        .append("<div class='bb-avatar'><img alt='' src='").append(escAttr(avatar)).append("'></div>")
                        .append("<div class='bb-userMeta'>")
                        .append("<div><b>").append(esc(username)).append("</b>")
                        .append(prot ? " <span class='pill pill-warn'>root</span>" : "")
                        .append("</div>")
                        .append("<div class='sub'>").append(esc("id: " + id)).append("</div>")
                        .append("</div>")
                        .append("</div>")
                        .append("</td>")
                        .append("<td>").append(esc(role)).append("</td>")
                        .append("<td>").append(status).append("</td>")
                        .append("</tr>");
            }
        }
        return sb.toString();
    }

    public String roleOptionsHtml() throws Exception {
        StringBuilder sb = new StringBuilder(2000);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT id, display_name FROM web_roles ORDER BY role_key ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong(1);
                String name = rs.getString(2);
                sb.append("<option value='").append(id).append("'>").append(esc(name)).append("</option>");
            }
        }
        if (sb.isEmpty()) sb.append("<option value='' disabled>(no roles)</option>");
        return sb.toString();
    }

    /**
     * For admin UI: dropdown of existing users excluding root.
     * Root must remain untouched.
     */
    public String userOptionsHtmlNonRoot() throws Exception {
        StringBuilder sb = new StringBuilder(4000);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT username FROM web_users WHERE LOWER(username) <> 'root' ORDER BY username ASC LIMIT 2000"
             );
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String u = rs.getString(1);
                if (u == null || u.isBlank()) continue;
                sb.append("<option value='").append(escAttr(u)).append("'>").append(esc(u)).append("</option>");
            }
        }
        if (sb.isEmpty()) sb.append("<option value='' disabled>(no users)</option>");
        return sb.toString();
    }

    public void createUserRaw(String username, String rawPassword, long roleId) throws Exception {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username missing");
        String u = username.trim();
        if ("root".equalsIgnoreCase(u)) throw new IllegalArgumentException("root is reserved");

        if (rawPassword == null) rawPassword = "";
        if (rawPassword.trim().length() < 8) throw new IllegalArgumentException("password too short (min 8)");

        if (roleId <= 0) throw new IllegalArgumentException("roleId missing");

        String hash = PasswordUtil.hashPbkdf2(rawPassword);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO web_users(username, password_hash, role_id, is_protected) VALUES(?, ?, ?, 0)"
             )) {
            ps.setString(1, u);
            ps.setString(2, hash);
            ps.setLong(3, roleId);
            ps.executeUpdate();
        }
    }

    public void setUserRole(String username, long roleId) throws Exception {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username missing");
        if (roleId <= 0) throw new IllegalArgumentException("roleId missing");

        String u = username.trim();
        if ("root".equalsIgnoreCase(u)) throw new IllegalArgumentException("root role cannot be changed");

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE web_users SET role_id=?, updated_at=CURRENT_TIMESTAMP(3) WHERE username=? LIMIT 1"
             )) {
            ps.setLong(1, roleId);
            ps.setString(2, u);
            ps.executeUpdate();
        }
    }

    public void resetPasswordRaw(String username, String rawPassword) throws Exception {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("username missing");
        String u = username.trim();

        if (rawPassword == null) rawPassword = "";
        if (rawPassword.trim().length() < 8) throw new IllegalArgumentException("new password too short (min 8)");

        String newHash = PasswordUtil.hashPbkdf2(rawPassword);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE web_users SET password_hash=?, updated_at=CURRENT_TIMESTAMP(3) WHERE username=? LIMIT 1"
             )) {
            ps.setString(1, newHash);
            ps.setString(2, u);
            ps.executeUpdate();
        }
    }

    public void changeOwnPassword(String username, String currentPassword, String newPassword) throws Exception {
        String u = (username == null) ? "" : username.trim();
        if (u.isBlank()) throw new IllegalArgumentException("username missing");

        if (newPassword == null) newPassword = "";
        if (newPassword.trim().length() < 8) throw new IllegalArgumentException("new password too short (min 8)");

        String existingHash;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT password_hash, disabled_at FROM web_users WHERE username=? LIMIT 1"
             )) {
            ps.setString(1, u);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("user not found");
                if (rs.getTimestamp("disabled_at") != null) throw new IllegalArgumentException("user disabled");
                existingHash = rs.getString("password_hash");
            }
        }

        if (!PasswordUtil.verifyPbkdf2(currentPassword, existingHash)) {
            throw new IllegalArgumentException("current password invalid");
        }

        String newHash = PasswordUtil.hashPbkdf2(newPassword);
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE web_users SET password_hash=?, updated_at=CURRENT_TIMESTAMP(3) WHERE username=? LIMIT 1"
             )) {
            ps.setString(1, newHash);
            ps.setString(2, u);
            ps.executeUpdate();
        }
    }

    private static boolean userExists(Connection c, String username) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM web_users WHERE username=? LIMIT 1")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    private static long roleIdByKey(Connection c, String roleKey) throws Exception {
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM web_roles WHERE role_key=? LIMIT 1")) {
            ps.setString(1, roleKey);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        }
    }

    private static String generateRootPassword() {
        byte[] b = new byte[18];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}