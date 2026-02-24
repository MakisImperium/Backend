package org.backendbridge;

import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Application configuration loaded from {@code backend.yml}.
 *
 * Supports:
 * - loadFromResource("backend.yml") for defaults shipped with the app
 * - load(Path) for user-editable external config file
 */
public record AppConfig(
        Web web,
        DbCfg db,
        ServerAuthCfg serverAuth,
        AdminCfg admin
) {

    public record Web(String bind, int port) {}
    public record DbCfg(String jdbcUrl, String username, String password) {}
    public record ServerAuthCfg(boolean enabled, String token) {}
    public record AdminCfg(String serverName, String rootPasswordHash) {}

    @SuppressWarnings("unchecked")
    public static AppConfig loadFromResource(String resourceName) {
        Yaml y = new Yaml();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (is == null) throw new IllegalStateException("Missing resource: " + resourceName);

            Map<String, Object> root = y.load(is);
            return fromRootMap(root, "resource:" + resourceName);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config from resource: " + resourceName, e);
        }
    }

    @SuppressWarnings("unchecked")
    public static AppConfig load(Path path) {
        if (path == null) throw new IllegalArgumentException("path is null");
        Yaml y = new Yaml();
        try {
            String text = Files.readString(path, StandardCharsets.UTF_8);
            Map<String, Object> root = y.load(text);
            return fromRootMap(root, path.toAbsolutePath().toString());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load config file: " + path.toAbsolutePath(), e);
        }
    }

    public static void ensureConfigFileExists(Path path) {
        if (path == null) throw new IllegalArgumentException("path is null");
        try {
            if (Files.exists(path)) return;

            Path parent = path.getParent();
            if (parent != null) Files.createDirectories(parent);

            Files.writeString(path, defaultYamlTemplate(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create config file: " + path.toAbsolutePath(), e);
        }
    }

    public static String defaultYamlTemplate() {
        return """
            # BackendBridge configuration (external, user-editable)
            #
            # Start options:
            #   - no args: uses ./backend.yml (created automatically if missing)
            #   - 1st arg: path to config file, e.g. C:\\path\\to\\backend.yml
            #
            # IMPORTANT:
            # - Put correct db.jdbcUrl, otherwise the DB pool cannot start.
            # - Fill placeholders like <DB_USER> with real values.

            web:
              bind: "0.0.0.0"
              port: 8080

            db:
              # If you see: "Public Key Retrieval is not allowed"
              # keep allowPublicKeyRetrieval=true for local/dev OR enable SSL properly.
              jdbcUrl: "jdbc:mysql://localhost:3306/banbridge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
              username: "<DB_USER>"
              password: "<DB_PASSWORD>"

            serverAuth:
              enabled: true
              token: "<SERVER_API_TOKEN>"

            admin:
              serverName: "MyServer"
              # Leave empty to auto-generate a root password on first start (printed to console once)
              rootPasswordHash: ""
            """;
    }

    @SuppressWarnings("unchecked")
    private static AppConfig fromRootMap(Map<String, Object> root, String sourceLabel) {
        if (root == null) root = Map.of();

        Map<String, Object> web = (Map<String, Object>) root.getOrDefault("web", Map.of());
        Map<String, Object> db = (Map<String, Object>) root.getOrDefault("db", Map.of());
        Map<String, Object> serverAuth = (Map<String, Object>) root.getOrDefault("serverAuth", Map.of());
        Map<String, Object> admin = (Map<String, Object>) root.getOrDefault("admin", Map.of());

        Web w = new Web(
                String.valueOf(web.getOrDefault("bind", "0.0.0.0")),
                Integer.parseInt(String.valueOf(web.getOrDefault("port", "8080")))
        );

        DbCfg d = new DbCfg(
                String.valueOf(db.getOrDefault("jdbcUrl", "")),
                String.valueOf(db.getOrDefault("username", "")),
                String.valueOf(db.getOrDefault("password", ""))
        );

        ServerAuthCfg sa = new ServerAuthCfg(
                Boolean.parseBoolean(String.valueOf(serverAuth.getOrDefault("enabled", "true"))),
                String.valueOf(serverAuth.getOrDefault("token", ""))
        );

        AdminCfg ac = new AdminCfg(
                String.valueOf(admin.getOrDefault("serverName", "MyServer")),
                String.valueOf(admin.getOrDefault("rootPasswordHash", ""))
        );

        AppConfig cfg = new AppConfig(w, d, sa, ac);
        validate(cfg, sourceLabel);
        return cfg;
    }

    private static void validate(AppConfig cfg, String sourceLabel) {
        if (cfg == null) throw new IllegalStateException("Config is null (" + sourceLabel + ")");
        if (cfg.db == null) throw new IllegalStateException("Missing 'db' section (" + sourceLabel + ")");

        String jdbcUrl = (cfg.db.jdbcUrl() == null) ? "" : cfg.db.jdbcUrl().trim();
        if (jdbcUrl.isBlank()) {
            throw new IllegalStateException(
                    "Config error (" + sourceLabel + "): db.jdbcUrl is missing/blank."
            );
        }
    }
}