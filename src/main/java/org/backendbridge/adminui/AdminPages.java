package org.backendbridge.adminui;

import org.backendbridge.repo.AdminRepository;
import org.backendbridge.repo.MetricsRepository;

import java.util.List;
import java.util.Map;

/**
 * Admin UI pages (SSR).
 *
 * Facade: keeps the public API stable while implementation is split across smaller classes.
 */
public final class AdminPages {

    private AdminPages() {}

    // ---------------- Login / Users / Roles ----------------

    public static String login(String serverName, Lang lang, String err) {
        return AdminPagesAuth.login(serverName, lang, err);
    }

    public static String messageBox(Lang lang, String ok, String err) {
        return AdminPagesAuth.messageBox(lang, ok, err);
    }

    public static String account(String serverName, Lang lang, String msgHtml, String username) {
        return AdminPagesAuth.account(serverName, lang, msgHtml, username);
    }

    public static String users(
            String serverName,
            Lang lang,
            String msgHtml,
            String roleOptionsHtml,
            String userOptionsHtmlNonRoot,
            String userRowsHtml
    ) {
        return AdminPagesAuth.users(serverName, lang, msgHtml, roleOptionsHtml, userOptionsHtmlNonRoot, userRowsHtml);
    }

    public static String rolesWithPermissions(
            String serverName,
            Lang lang,
            String msgHtml,
            List<AdminRepository.WebRole> roles,
            List<AdminRepository.WebPerm> perms,
            Map<Long, Map<String, Boolean>> rolePerms
    ) {
        return AdminPagesAuth.rolesWithPermissions(serverName, lang, msgHtml, roles, perms, rolePerms);
    }

    // ---------------- Audit ----------------

    public static String audit(String serverName, Lang lang, String msgHtml, String filtersHtml, String rowsHtml, int resultCount) {
        return AdminPagesAuth.audit(serverName, lang, msgHtml, filtersHtml, rowsHtml, resultCount);
    }

    public static String auditFilterForm(Lang lang, String actor, String action, String q, String limit) {
        return AdminPagesAuth.auditFilterForm(lang, actor, action, q, limit);
    }

    public static String auditRow(String createdAtIso, String actorUsername, String actionKey, String details, long id) {
        return AdminPagesAuth.auditRow(createdAtIso, actorUsername, actionKey, details, id);
    }

    // ---------------- Players / Bans / Stats ----------------

    public static String players(String serverName, Lang lang, long totalPlayers, long playersWithStats, long activeBans, long totalBans, String rowsHtml) {
        return AdminPagesGame.players(serverName, lang, totalPlayers, playersWithStats, activeBans, totalBans, rowsHtml);
    }

    public static String bans(String serverName, Lang lang, long totalPlayers, long activeBans, long revokedBans, long totalBans, String rowsHtml) {
        return AdminPagesGame.bans(serverName, lang, totalPlayers, activeBans, revokedBans, totalBans, rowsHtml);
    }

    /**
     * Monitoring dashboard page.
     * Uses SSE endpoint /admin/api/live/stream and fetches JSON from /admin/api/live/stats/history.
     */
    public static String serverStats(String serverName, Lang lang, String serverKeyOrNull, MetricsRepository.Metrics latest) {
        return AdminPagesGame.serverStats(serverName, lang, serverKeyOrNull, latest);
    }

    public static String playerNotFound(String serverName, Lang lang) {
        return AdminPagesGame.playerNotFound(serverName, lang);
    }

    public static String playerDetail(
            String serverName,
            Lang lang,
            String xuid,
            String name,
            boolean online,
            String onlineUpdatedIso,
            String lastSeenIso,
            long playtimeSeconds,
            long kills,
            long deaths,
            boolean hasActiveBan,
            long banId,
            String banReason,
            String banCreatedAtIso,
            String banExpiresAtIso,
            String banUpdatedAtIso,
            List<AdminRepository.BanEvent> events
    ) {
        return AdminPagesGame.playerDetail(
                serverName,
                lang,
                xuid,
                name,
                online,
                onlineUpdatedIso,
                lastSeenIso,
                playtimeSeconds,
                kills,
                deaths,
                hasActiveBan,
                banId,
                banReason,
                banCreatedAtIso,
                banExpiresAtIso,
                banUpdatedAtIso,
                events
        );
    }

    public static String playerRow(
            String xuid,
            String name,
            boolean online,
            String onlineUpdatedIso,
            long playtimeSeconds,
            long kills,
            long deaths,
            String lastSeenIso,
            String statsUpdatedIso
    ) {
        return AdminPagesGame.playerRow(
                xuid,
                name,
                online,
                onlineUpdatedIso,
                playtimeSeconds,
                kills,
                deaths,
                lastSeenIso,
                statsUpdatedIso
        );
    }

    public static String banRow(
            long banId,
            String xuid,
            String reason,
            String createdAtIso,
            String expiresAtIsoOrNull,
            String revokedAtIsoOrNull,
            String updatedAtIso,
            boolean active
    ) {
        return AdminPagesGame.banRow(banId, xuid, reason, createdAtIso, expiresAtIsoOrNull, revokedAtIsoOrNull, updatedAtIso, active);
    }
}