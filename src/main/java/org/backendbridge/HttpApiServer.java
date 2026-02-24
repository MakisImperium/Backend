package org.backendbridge;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.backendbridge.adminui.Lang;
import org.backendbridge.repo.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;

public final class HttpApiServer {

    private static final String LANG_COOKIE = "bb_lang";

    private final AppConfig cfg;
    private final Db db;

    private final AuthService serverAuth;
    private final AdminAuth adminAuth;

    private final StatsRepository statsRepo;
    private final BansRepository bansRepo;
    private final MetricsRepository metricsRepo;
    private final PresenceRepository presenceRepo;
    private final CommandsRepository commandsRepo;

    private final AdminRepository adminRepo;

    private HttpServer server;

    public HttpApiServer(
            AppConfig cfg,
            Db db,
            AuthService serverAuth,
            AdminAuth adminAuth,
            StatsRepository statsRepo,
            BansRepository bansRepo,
            MetricsRepository metricsRepo,
            PresenceRepository presenceRepo,
            CommandsRepository commandsRepo,
            AdminRepository adminRepo
    ) {
        this.cfg = cfg;
        this.db = db;
        this.serverAuth = serverAuth;
        this.adminAuth = adminAuth;
        this.statsRepo = statsRepo;
        this.bansRepo = bansRepo;
        this.metricsRepo = metricsRepo;
        this.presenceRepo = presenceRepo;
        this.commandsRepo = commandsRepo;
        this.adminRepo = adminRepo;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(cfg.web().bind(), cfg.web().port()), 0);
        server.setExecutor(Executors.newFixedThreadPool(12));

        // ---------------- API ----------------

        server.createContext("/api/server/health", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            boolean ok = db.ping();
            sendJson(ex, 200, "{\"status\":\"ok\",\"serverTime\":\"" + Instant.now() + "\",\"dbOk\":" + ok + "}");
        }));

        server.createContext("/api/server/stats/batch", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            try {
                JsonNode root = JsonUtil.OM.readTree(ex.getRequestBody().readAllBytes());
                JsonNode players = root.get("players");
                if (players == null || !players.isArray()) {
                    sendJson(ex, 400, "{\"error\":\"bad_request\",\"details\":\"players array missing\"}");
                    return;
                }
                statsRepo.persistStatsPlayersArray(players);
                sendEmpty(ex, 200);
            } catch (JsonProcessingException e) {
                sendJson(ex, 400, "{\"error\":\"bad_request\",\"details\":\"invalid_json\"}");
            }
        }));

        server.createContext("/api/server/bans/changes", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            String since = queryParam(ex, "since");
            if (since == null || since.isBlank()) since = "1970-01-01T00:00:00Z";
            sendJson(ex, 200, bansRepo.fetchBanChangesJson(since));
        }));

        server.createContext("/api/server/metrics", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            JsonNode root = JsonUtil.OM.readTree(ex.getRequestBody().readAllBytes());
            String serverKey = root.path("serverKey").asText(null);
            if (serverKey == null || serverKey.isBlank()) {
                sendJson(ex, 400, "{\"error\":\"bad_request\",\"details\":\"serverKey missing\"}");
                return;
            }

            metricsRepo.ingest(serverKey, root);
            sendEmpty(ex, 200);
        }));

        server.createContext("/api/server/presence", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            JsonNode root = JsonUtil.OM.readTree(ex.getRequestBody().readAllBytes());
            JsonNode players = root.get("players");
            if (players == null || !players.isArray()) {
                sendJson(ex, 400, "{\"error\":\"bad_request\",\"details\":\"players array missing\"}");
                return;
            }

            presenceRepo.upsertPresencePlayersArray(players);
            sendEmpty(ex, 200);
        }));

        server.createContext("/api/server/bans/report", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            JsonNode root = JsonUtil.OM.readTree(ex.getRequestBody().readAllBytes());
            String serverKey = root.path("serverKey").asText(null);
            JsonNode ban = root.get("ban");
            if (serverKey == null || serverKey.isBlank() || ban == null || ban.isNull()) {
                sendJson(ex, 400, "{\"error\":\"bad_request\"}");
                return;
            }

            bansRepo.reportServerBan(serverKey, ban);
            sendEmpty(ex, 200);
        }));

        server.createContext("/api/server/commands/poll", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            String serverKey = queryParam(ex, "serverKey");
            if (serverKey == null || serverKey.isBlank()) {
                sendJson(ex, 400, "{\"error\":\"bad_request\",\"details\":\"serverKey missing\"}");
                return;
            }

            long sinceId = 0L;
            try { sinceId = Long.parseLong(String.valueOf(queryParam(ex, "sinceId"))); } catch (Exception ignored) {}

            sendJson(ex, 200, commandsRepo.pollOpenCommandsJson(serverKey, sinceId, 50));
        }));

        server.createContext("/api/server/commands/ack", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!serverAuth.isAuthorized(ex)) {
                sendJson(ex, 401, "{\"error\":\"unauthorized\"}");
                return;
            }

            JsonNode root = JsonUtil.OM.readTree(ex.getRequestBody().readAllBytes());
            String serverKey = root.path("serverKey").asText(null);
            long id = root.path("id").asLong(0);

            if (serverKey == null || serverKey.isBlank() || id <= 0) {
                sendJson(ex, 400, "{\"error\":\"bad_request\"}");
                return;
            }

            commandsRepo.ackCommand(serverKey, id);
            sendEmpty(ex, 200);
        }));

        // ---------------- Admin language ----------------

        server.createContext("/admin/lang", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");

            String set = queryParam(ex, "set");
            Lang lang = Lang.fromStringOrNull(set);
            if (lang != null) {
                setCookie(ex, LANG_COOKIE, lang.cookieValue(), false, 365 * 24 * 3600);
            }

            String back = queryParam(ex, "back");
            if (back == null || back.isBlank()) back = "/admin/players";
            if (!back.startsWith("/")) back = "/admin/players";
            redirect(ex, back);
        }));

        // ---------------- Admin pages ----------------

        server.createContext("/admin/login", ex -> handleSafely(ex, () -> {
            Lang lang = requestLang(ex);

            if (isMethod(ex, "GET")) {
                sendHtml(ex, 200, adminRepo.renderLoginHtml(lang, queryParam(ex, "err")));
                return;
            }
            requireMethod(ex, "POST");

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String user = formField(body, "username");
            String pass = formField(body, "password");

            boolean ok = adminAuth.login(ex, user, pass);
            redirect(ex, ok ? "/admin/players" : "/admin/login?err=bad_credentials");
        }));

        server.createContext("/admin/logout", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            adminAuth.logout(ex);
            redirect(ex, "/admin/login");
        }));

        server.createContext("/admin/account", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;

            Lang lang = requestLang(ex);
            String username = adminAuth.loggedInUsername(ex);
            sendHtml(ex, 200, adminRepo.renderAccountHtml(lang, queryParam(ex, "ok"), queryParam(ex, "err"), username));
        }));

        server.createContext("/admin/account/password", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;

            String username = adminAuth.loggedInUsername(ex);
            if (username == null || username.isBlank()) {
                redirect(ex, "/admin/login");
                return;
            }

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String currentPassword = formField(body, "currentPassword");
            String newPassword = formField(body, "newPassword");
            String newPassword2 = formField(body, "newPassword2");

            if (newPassword == null || newPassword.isBlank() || !Objects.equals(newPassword, newPassword2)) {
                redirect(ex, "/admin/account?err=" + urlEncodeQuery("Password confirmation does not match"));
                return;
            }

            try {
                adminRepo.changeOwnPassword(username, currentPassword, newPassword);
                redirect(ex, "/admin/account?ok=" + urlEncodeQuery("Password updated"));
            } catch (IllegalArgumentException iae) {
                redirect(ex, "/admin/account?err=" + urlEncodeQuery(String.valueOf(iae.getMessage())));
            }
        }));

        server.createContext("/admin/players", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "players.view")) return;

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderPlayersHtml(lang));
        }));

        server.createContext("/admin/player/ban", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "players.view")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String xuid = formField(body, "xuid");
            String reason = formField(body, "reason");

            Integer hours = null;
            try {
                String h = formField(body, "hours");
                if (h != null && !h.isBlank()) hours = Integer.parseInt(h.trim());
            } catch (Exception ignored) {}

            adminRepo.banPlayerByXuid(xuid, reason, hours);
            redirect(ex, "/admin/player?xuid=" + urlEncodeQuery(xuid) + "&ok=" + urlEncodeQuery("Banned"));
        }));

        server.createContext("/admin/player/unban", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "players.view")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String xuid = formField(body, "xuid");

            adminRepo.unbanPlayerByXuid(xuid);
            redirect(ex, "/admin/player?xuid=" + urlEncodeQuery(xuid) + "&ok=" + urlEncodeQuery("Unbanned"));
        }));

        server.createContext("/admin/player", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "players.view")) return;

            String xuid = queryParam(ex, "xuid");
            if (xuid == null || xuid.isBlank()) {
                redirect(ex, "/admin/players");
                return;
            }

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderPlayerDetailHtml(lang, xuid));
        }));

        server.createContext("/admin/bans", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "bans.view")) return;

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderBansHtml(lang));
        }));

        server.createContext("/admin/users", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "users.manage")) return;

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderUsersHtml(lang, queryParam(ex, "ok"), queryParam(ex, "err")));
        }));

        server.createContext("/admin/users/create", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "users.manage")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            long roleId = 0;
            try { roleId = Long.parseLong(Objects.toString(formField(body, "roleId"), "0").trim()); } catch (Exception ignored) {}

            adminRepo.createUser(formField(body, "username"), formField(body, "password"), roleId);
            redirect(ex, "/admin/users?ok=" + urlEncodeQuery("User created"));
        }));

        server.createContext("/admin/users/role/set", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "users.manage")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            long roleId = 0;
            try { roleId = Long.parseLong(Objects.toString(formField(body, "roleId"), "0").trim()); } catch (Exception ignored) {}

            adminRepo.setUserRole(formField(body, "username"), roleId);
            redirect(ex, "/admin/users?ok=" + urlEncodeQuery("Role updated"));
        }));

        server.createContext("/admin/users/reset", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "users.manage")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            adminRepo.resetPassword(formField(body, "username"), formField(body, "password"));
            redirect(ex, "/admin/users?ok=" + urlEncodeQuery("Password reset"));
        }));

        server.createContext("/admin/roles", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "roles.manage")) return;

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderRolesHtml(lang, queryParam(ex, "ok"), queryParam(ex, "err")));
        }));

        server.createContext("/admin/roles/create", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "roles.manage")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            adminRepo.createRole(formField(body, "roleKey"), formField(body, "displayName"));
            redirect(ex, "/admin/roles?ok=" + urlEncodeQuery("Role created"));
        }));

        server.createContext("/admin/roles/perms/set", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "POST");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "roles.manage")) return;

            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

            long roleId = 0;
            try { roleId = Long.parseLong(Objects.toString(formField(body, "roleId"), "0").trim()); } catch (Exception ignored) {}

            String permKey = formField(body, "permKey");
            String enabledStr = formField(body, "enabled");
            boolean enabled = "1".equals(enabledStr) || "true".equalsIgnoreCase(enabledStr) || "on".equalsIgnoreCase(enabledStr);

            adminRepo.setRolePermission(roleId, permKey, enabled);
            sendEmpty(ex, 200);
        }));

        server.createContext("/admin/stats", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "stats.view")) return;

            Lang lang = requestLang(ex);
            sendHtml(ex, 200, adminRepo.renderServerStatsHtml(lang, queryParam(ex, "serverKey")));
        }));

        // ---------------- NEW: Audit page ----------------

        server.createContext("/admin/audit", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "audit.view")) return;

            Lang lang = requestLang(ex);

            String actor = queryParam(ex, "actor");
            String action = queryParam(ex, "action");
            String q = queryParam(ex, "q");

            Integer limit = null;
            try {
                String lim = queryParam(ex, "limit");
                if (lim != null && !lim.isBlank()) limit = Integer.parseInt(lim.trim());
            } catch (Exception ignored) {}

            sendHtml(ex, 200, adminRepo.renderAuditHtml(lang, queryParam(ex, "ok"), queryParam(ex, "err"), actor, action, q, limit));
        }));

        // -------- Admin live endpoints --------

        server.createContext("/admin/api/live/stats/history", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;
            if (!requirePerm(ex, "stats.view")) return;

            String serverKey = queryParam(ex, "serverKey");
            int limit = 600;
            try { limit = Integer.parseInt(Objects.toString(queryParam(ex, "limit"), "600").trim()); } catch (Exception ignored) {}

            sendJson(ex, 200, adminRepo.statsHistoryJson(serverKey, limit));
        }));

        server.createContext("/admin/api/live/stream", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            if (!requireAdmin(ex)) return;

            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache, no-store, must-revalidate");
            ex.getResponseHeaders().set("Pragma", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0);

            LiveBus.Subscriber sub = LiveBus.subscribe();
            try (OutputStream os = ex.getResponseBody()) {
                try {
                    writeSse(os, "hello", "{\"ok\":true}");
                    os.flush();

                    while (true) {
                        LiveBus.SseEvent ev = sub.poll(15_000);
                        if (ev == null) {
                            os.write((": keep-alive\n\n").getBytes(StandardCharsets.UTF_8));
                            os.flush();
                            continue;
                        }
                        writeSse(os, ev.event(), ev.dataJson());
                        os.flush();
                    }
                } catch (IOException clientDisconnected) {
                    // ignore
                }
            } finally {
                LiveBus.unsubscribe(sub.id());
            }
        }));

        server.createContext("/", ex -> handleSafely(ex, () -> {
            requireMethod(ex, "GET");
            redirect(ex, "/admin/players");
        }));

        server.start();
        System.out.println("[BackendBridgeService] Listening on http://" + cfg.web().bind() + ":" + cfg.web().port());
        System.out.println("[BackendBridgeService] Server auth enabled: " + serverAuth.isEnabled());
    }

    public void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler { void run() throws Exception; }

    private static void handleSafely(HttpExchange ex, ExchangeHandler h) {
        try {
            h.run();
        } catch (MethodNotAllowed e) {
            safeHtml(ex, 405, "<h1>405</h1>");
        } catch (IOException clientDisconnected) {
            // ignore
        } catch (Exception e) {
            safeHtml(ex, 500, "<h1>500</h1><pre>" + esc(e) + "</pre>");
        } finally {
            try { ex.close(); } catch (Exception ignored) {}
        }
    }

    private static void safeHtml(HttpExchange ex, int status, String html) {
        try {
            sendHtml(ex, status, html);
        } catch (Exception ignored) {
            try { ex.sendResponseHeaders(status, -1); } catch (Exception ignored2) {}
        }
    }

    private boolean requireAdmin(HttpExchange ex) throws IOException {
        if (adminAuth.isLoggedIn(ex)) return true;
        redirect(ex, "/admin/login");
        return false;
    }

    private boolean requirePerm(HttpExchange ex, String permKey) throws IOException {
        if (adminAuth.hasPermission(ex, permKey)) return true;
        redirect(ex, "/admin/players?err=forbidden");
        return false;
    }

    private static boolean isMethod(HttpExchange ex, String method) {
        return method.equalsIgnoreCase(ex.getRequestMethod());
    }

    private static void requireMethod(HttpExchange ex, String method) throws MethodNotAllowed {
        if (!isMethod(ex, method)) throw new MethodNotAllowed();
    }

    private static final class MethodNotAllowed extends Exception {}

    private static Lang requestLang(HttpExchange ex) {
        return Lang.fromCookieOrDefault(cookie(ex, LANG_COOKIE));
    }

    private static String cookie(HttpExchange ex, String name) {
        String raw = ex.getRequestHeaders().getFirst("Cookie");
        if (raw == null || raw.isBlank()) return null;

        String[] parts = raw.split(";");
        for (String p : parts) {
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

    private static void redirect(HttpExchange ex, String location) throws IOException {
        ex.getResponseHeaders().set("Location", location);
        ex.sendResponseHeaders(302, -1);
    }

    private static void sendEmpty(HttpExchange ex, int status) throws IOException {
        ex.sendResponseHeaders(status, -1);
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendHtml(HttpExchange ex, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void writeSse(OutputStream os, String event, String jsonOneLine) throws IOException {
        os.write(("event: " + event + "\n").getBytes(StandardCharsets.UTF_8));
        os.write(("data: " + (jsonOneLine == null ? "{}" : jsonOneLine) + "\n\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            int idx = part.indexOf('=');
            if (idx <= 0) continue;
            String k = part.substring(0, idx);
            String v = part.substring(idx + 1);
            if (key.equals(urlDecode(k))) return urlDecode(v);
        }
        return null;
    }

    private static String formField(String urlEncodedBody, String key) {
        if (urlEncodedBody == null) return null;
        for (String part : urlEncodedBody.split("&")) {
            int idx = part.indexOf('=');
            if (idx < 0) continue;
            String k = part.substring(0, idx);
            String v = part.substring(idx + 1);
            if (key.equals(urlDecode(k))) return urlDecode(v);
        }
        return null;
    }

    private static String urlDecode(String s) {
        try { return URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    private static String urlEncodeQuery(String s) {
        if (s == null) return "";
        return s.replace("%", "%25")
                .replace(" ", "%20")
                .replace("+", "%2B")
                .replace("&", "%26")
                .replace("=", "%3D")
                .replace("?", "%3F");
    }

    private static String esc(Exception e) {
        String m = e.getClass().getName() + ": " + String.valueOf(e.getMessage());
        return m.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}