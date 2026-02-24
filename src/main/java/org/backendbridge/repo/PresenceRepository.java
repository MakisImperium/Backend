package org.backendbridge.repo;

import com.fasterxml.jackson.databind.JsonNode;
import org.backendbridge.Db;
import org.backendbridge.LiveBus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Persists online/offline presence reported by the game server.
 *
 * Final, reliable modes:
 *
 * 1) Snapshot mode (RECOMMENDED):
 *    - Request body contains: {"snapshot": true, "players":[ ...online players... ]}
 *    - Any player not included in the snapshot will be marked offline in DB.
 *    - This guarantees correct online/offline even without explicit offline events.
 *
 * 2) Event mode:
 *    - Request body contains: {"players":[ {xuid, online:true/false, ...}, ... ]}
 *    - Only updates the players contained in the payload (no global offline marking).
 */
public final class PresenceRepository {

    private final Db db;

    public PresenceRepository(Db db) {
        this.db = db;
    }

    public void upsertPresencePlayersArray(JsonNode rootOrPlayersArray) throws Exception {
        if (rootOrPlayersArray == null || rootOrPlayersArray.isNull()) return;

        final boolean snapshotMode;
        final JsonNode playersArray;

        if (rootOrPlayersArray.isArray()) {
            // Backward compatible: old clients POST just an array.
            snapshotMode = false;
            playersArray = rootOrPlayersArray;
        } else {
            snapshotMode = boolVal(rootOrPlayersArray, "snapshot", false);
            playersArray = rootOrPlayersArray.get("players");
        }

        if (playersArray == null || !playersArray.isArray()) return;

        // Collect xuids from snapshot (online set)
        Set<String> snapshotXuIds = snapshotMode ? new HashSet<>() : Set.of();

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (JsonNode p : playersArray) {
                    String xuid = text(p, "xuid");
                    if (xuid == null || xuid.isBlank()) continue;

                    String name = text(p, "name");

                    // In snapshot mode, any entry is implicitly online unless online=false is explicitly provided.
                    boolean online = snapshotMode
                            ? boolVal(p, "online", true)
                            : boolVal(p, "online", false);

                    String ip = text(p, "ip");
                    String hwid = text(p, "hwid");

                    upsertPresenceOne(c, xuid, name, online, ip, hwid);

                    if (snapshotMode && online) snapshotXuIds.add(xuid);
                }

                if (snapshotMode) {
                    markAllOthersOffline(c, snapshotXuIds);
                }

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        LiveBus.publishInvalidate("players");
    }

    private void upsertPresenceOne(Connection c, String xuid, String name, boolean online, String ip, String hwid) throws Exception {
        // Strategy:
        // - If online=true: bump last_seen_at and online_updated_at to CURRENT_TIMESTAMP
        // - If online=false: do NOT bump last_seen_at (otherwise you "see" them while offline)
        // - Always update online + online_updated_at to reflect the latest state change
        // - Only overwrite last_ip/last_hwid when provided (keep previous otherwise)
        String safeName = (name == null || name.isBlank()) ? "Unknown" : name;

        if (online) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO players(" +
                            "xuid, last_name, last_seen_at, online, online_updated_at, last_ip, last_hwid" +
                            ") VALUES(?, ?, CURRENT_TIMESTAMP(3), 1, CURRENT_TIMESTAMP(3), ?, ?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "last_name=VALUES(last_name), " +
                            "last_seen_at=CURRENT_TIMESTAMP(3), " +
                            "online=1, " +
                            "online_updated_at=CURRENT_TIMESTAMP(3), " +
                            "last_ip=COALESCE(VALUES(last_ip), players.last_ip), " +
                            "last_hwid=COALESCE(VALUES(last_hwid), players.last_hwid)"
            )) {
                ps.setString(1, xuid);
                ps.setString(2, safeName);
                ps.setObject(3, (ip == null || ip.isBlank()) ? null : ip);
                ps.setObject(4, (hwid == null || hwid.isBlank()) ? null : hwid);
                ps.executeUpdate();
            }
            return;
        }

        // offline update
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO players(" +
                        "xuid, last_name, last_seen_at, online, online_updated_at, last_ip, last_hwid" +
                        ") VALUES(?, ?, CURRENT_TIMESTAMP(3), 0, CURRENT_TIMESTAMP(3), ?, ?) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "last_name=COALESCE(VALUES(last_name), players.last_name), " +
                        "online=0, " +
                        "online_updated_at=CURRENT_TIMESTAMP(3), " +
                        "last_ip=COALESCE(VALUES(last_ip), players.last_ip), " +
                        "last_hwid=COALESCE(VALUES(last_hwid), players.last_hwid)"
        )) {
            ps.setString(1, xuid);
            ps.setString(2, safeName);
            ps.setObject(3, (ip == null || ip.isBlank()) ? null : ip);
            ps.setObject(4, (hwid == null || hwid.isBlank()) ? null : hwid);
            ps.executeUpdate();
        }
    }

    private void markAllOthersOffline(Connection c, Set<String> onlineXuIds) throws Exception {
        // If snapshot is empty => everyone becomes offline.
        if (onlineXuIds == null || onlineXuIds.isEmpty()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE players SET online=0, online_updated_at=CURRENT_TIMESTAMP(3) WHERE online=1"
            )) {
                ps.executeUpdate();
            }
            return;
        }

        // Build: UPDATE players SET online=0 ... WHERE online=1 AND xuid NOT IN (?,?,...)
        // We cap the IN-list to avoid pathological payload sizes.
        List<String> ids = new ArrayList<>(onlineXuIds);
        int cap = Math.min(ids.size(), 2000);

        StringBuilder sb = new StringBuilder(64 + cap * 2);
        sb.append("UPDATE players SET online=0, online_updated_at=CURRENT_TIMESTAMP(3) WHERE online=1 AND xuid NOT IN (");
        for (int i = 0; i < cap; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        sb.append(')');

        try (PreparedStatement ps = c.prepareStatement(sb.toString())) {
            int idx = 1;
            for (int i = 0; i < cap; i++) ps.setString(idx++, ids.get(i));
            ps.executeUpdate();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = (n == null) ? null : n.get(field);
        return (v == null || v.isNull()) ? null : v.asText(null);
    }

    private static boolean boolVal(JsonNode n, String field, boolean defaultValue) {
        JsonNode v = (n == null) ? null : n.get(field);
        if (v == null || v.isNull()) return defaultValue;
        if (v.isBoolean()) return v.asBoolean(defaultValue);
        if (v.isNumber()) return v.asInt(0) != 0;
        String s = v.asText("");
        if (s.isBlank()) return defaultValue;
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }
}