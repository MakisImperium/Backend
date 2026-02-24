package org.backendbridge;

import org.backendbridge.repo.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * Application entrypoint.
 *
 * Boot:
 * - ensure external config exists (create template if missing)
 * - load external config
 * - connect DB
 * - ensure root exists
 * - start HTTP server
 * - start console commands (/stop, stop, exit, quit, resetroot <pw>)
 */
public final class BackendMain {

    private BackendMain() {}

    public static void main(String[] args) throws Exception {
        Path cfgPath = (args.length >= 1 && args[0] != null && !args[0].isBlank())
                ? Path.of(args[0].trim())
                : Path.of("backend.yml");

        AppConfig.ensureConfigFileExists(cfgPath);
        System.out.println("[BackendBridgeService] Using config: " + cfgPath.toAbsolutePath());

        AppConfig cfg = AppConfig.load(cfgPath);

        Db db = new Db(cfg.db());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(null, db), "bb-shutdown"));

        AuthService serverAuth = new AuthService(cfg.serverAuth());

        UsersRepository usersRepo = new UsersRepository(db);
        usersRepo.ensureRootExists(cfg.admin().rootPasswordHash());

        MetricsRepository metricsRepo = new MetricsRepository(db);
        StatsRepository statsRepo = new StatsRepository(db);
        PresenceRepository presenceRepo = new PresenceRepository(db);

        BansRepository bansRepo = new BansRepository(db, 500);
        CommandsRepository commandsRepo = new CommandsRepository(db);

        AdminAuth adminAuth = new AdminAuth(db);
        AdminRepository adminRepo = new AdminRepository(db, cfg.admin().serverName(), usersRepo, metricsRepo);

        HttpApiServer http = new HttpApiServer(
                cfg,
                db,
                serverAuth,
                adminAuth,
                statsRepo,
                bansRepo,
                metricsRepo,
                presenceRepo,
                commandsRepo,
                adminRepo
        );

        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdown(http, db), "bb-shutdown-2"));

        http.start();
        startConsoleThread(http, db, usersRepo);

        Thread.currentThread().join();
    }

    private static void shutdown(HttpApiServer http, Db db) {
        try { if (http != null) http.stop(); } catch (Exception ignored) {}
        try { if (db != null) db.close(); } catch (Exception ignored) {}
    }

    private static void startConsoleThread(HttpApiServer http, Db db, UsersRepository usersRepo) {
        Thread t = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                System.out.println("[BackendBridgeService] Commands: /stop | stop | exit | quit | resetroot <newPassword>");

                String line;
                while ((line = br.readLine()) != null) {
                    String cmdLine = line.trim();
                    if (cmdLine.isEmpty()) continue;

                    if (equalsAnyIgnoreCase(cmdLine, "/stop", "stop", "exit", "quit")) {
                        System.out.println("[BackendBridgeService] Stopping...");
                        shutdown(http, db);
                        System.out.println("[BackendBridgeService] Bye.");
                        System.exit(0);
                        return;
                    }

                    if (cmdLine.regionMatches(true, 0, "resetroot ", 0, "resetroot ".length())) {
                        String newPass = cmdLine.substring("resetroot ".length()).trim();
                        if (newPass.isBlank()) {
                            System.out.println("[BackendBridgeService] Usage: resetroot <newPassword>");
                            continue;
                        }
                        try {
                            usersRepo.resetPasswordRaw("root", newPass);
                            System.out.println("[BackendBridgeService] Root password updated.");
                        } catch (Exception e) {
                            System.out.println("[BackendBridgeService] resetroot failed: " + e.getClass().getSimpleName());
                        }
                        continue;
                    }

                    System.out.println("[BackendBridgeService] Unknown command: " + cmdLine);
                }
            } catch (Exception e) {
                System.out.println("[BackendBridgeService] Console input disabled: " + e.getClass().getSimpleName());
            }
        }, "bb-console");

        t.setDaemon(true);
        t.start();
    }

    private static boolean equalsAnyIgnoreCase(String s, String... any) {
        for (String a : any) if (a != null && a.equalsIgnoreCase(s)) return true;
        return false;
    }
}