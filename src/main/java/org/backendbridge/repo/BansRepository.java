package org.backendbridge.repo;

import com.fasterxml.jackson.databind.JsonNode;
import org.backendbridge.Db;
import org.backendbridge.Json;
import org.backendbridge.LiveBus;

import java.sql.*;
import java.time.Instant;

/**
 * Ban persistence and server-to-backend ban reporting.
 *
 * <p>Features:</p>
 * <ul>
 *   <li>fetchBanChangesJson(sinceIso): for game server sync</li>
 *   <li>reportServerBan(serverKey, banNode): store + audit + targets</li>
 * </ul>
 */
public final class BansRepository {

    private final Db db;
    private final int maxRows;

    public BansRepository(Db db, int maxRows) {
        this.db = db;
        this.maxRows = Math.max(1, maxRows);
    }

    public String fetchBanChangesJson(String sinceIso) throws Exception {
        Instant since;
        try {
            since = Instant.parse(sinceIso);
        } catch (Exception e) {
            since = Instant.parse("1970-01-01T00:00:00Z");
        }

        StringBuilder out = new StringBuilder(32_000);
        out.append("{\"serverTime\":").append(Json.js(Instant.now().toString())).append(",\"changes\":[");

        boolean first = true;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT ban_id, xuid, reason, created_at, expires_at, revoked_at, updated_at " +
                             "FROM bans " +
                             "WHERE updated_at > ? " +
                             "ORDER BY updated_at ASC " +
                             "LIMIT " + maxRows
             )) {
            ps.setTimestamp(1, Timestamp.from(since));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;

                    long banId = rs.getLong("ban_id");
                    String xuid = rs.getString("xuid");
                    String reason = rs.getString("reason");

                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    Timestamp expTs = rs.getTimestamp("expires_at");
                    Timestamp revTs = rs.getTimestamp("revoked_at");
                    Instant updatedAt = rs.getTimestamp("updated_at").toInstant();

                    out.append("{")
                            .append("\"type\":\"BAN_UPSERT\",")
                            .append("\"banId\":").append(banId).append(',')
                            .append("\"xuid\":").append(Json.js(xuid)).append(',')
                            .append("\"reason\":").append(Json.js(reason)).append(',')
                            .append("\"createdAt\":").append(Json.js(createdAt.toString())).append(',')
                            .append("\"expiresAt\":").append(expTs == null ? "null" : Json.js(expTs.toInstant().toString())).append(',')
                            .append("\"revokedAt\":").append(revTs == null ? "null" : Json.js(revTs.toInstant().toString())).append(',')
                            .append("\"updatedAt\":").append(Json.js(updatedAt.toString()))
                            .append("}");
                }
            }
        }

        out.append("]}");
        return out.toString();
    }

    /**
     * Server reports a ban that was already enforced on the game server.
     * Writes:
     * - players stub (if missing)
     * - bans row
     * - ban_targets rows (xuid + optional ip/hwid)
     * - ban_events audit trail
     *
     * Publishes LiveBus invalidate("bans","players").
     */
    public void reportServerBan(String serverKey, JsonNode banNode) throws Exception {
        if (serverKey == null || serverKey.isBlank()) throw new IllegalArgumentException("serverKey missing");

        String xuid = text(banNode, "xuid");
        if (xuid == null || xuid.isBlank()) throw new IllegalArgumentException("xuid missing");

        String reason = text(banNode, "reason");
        if (reason == null || reason.isBlank()) reason = "No reason";

        String ip = text(banNode, "ip");
        String hwid = text(banNode, "hwid");

        Timestamp expiresAt = null;
        Long durationSeconds = longOrNull(banNode, "durationSeconds");
        if (durationSeconds != null && durationSeconds > 0) {
            expiresAt = Timestamp.from(Instant.now().plusSeconds(durationSeconds));
        }

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                // ensure player exists
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO players(xuid, last_name, first_seen_at, last_seen_at) " +
                                "VALUES(?, 'Unknown', CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)) " +
                                "ON DUPLICATE KEY UPDATE last_seen_at=CURRENT_TIMESTAMP(3)"
                )) {
                    ps.setString(1, xuid);
                    ps.executeUpdate();
                }

                long banId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO bans(xuid, reason, created_at, expires_at, revoked_at, updated_at, actor_type, actor_username, actor_server_key) " +
                                "VALUES(?, ?, CURRENT_TIMESTAMP(3), ?, NULL, CURRENT_TIMESTAMP(3), 'SERVER', NULL, ?)",
                        Statement.RETURN_GENERATED_KEYS
                )) {
                    ps.setString(1, xuid);
                    ps.setString(2, reason);
                    ps.setTimestamp(3, expiresAt);
                    ps.setString(4, serverKey);
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("ban_id missing");
                        banId = keys.getLong(1);
                    }
                }

                insertTarget(c, banId, "XUID", xuid);
                if (ip != null && !ip.isBlank()) insertTarget(c, banId, "IP", ip);
                if (hwid != null && !hwid.isBlank()) insertTarget(c, banId, "HWID", hwid);

                insertEvent(c, banId, "CREATED", "SERVER", null, serverKey, null);
                insertEvent(c, banId, "ENFORCED", "SERVER", null, serverKey, "enforced by game server");

                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        LiveBus.publishInvalidate("bans", "players");
    }

    private static void insertTarget(Connection c, long banId, String type, String value) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT IGNORE INTO ban_targets(ban_id, target_type, target_value) VALUES(?,?,?)"
        )) {
            ps.setLong(1, banId);
            ps.setString(2, type);
            ps.setString(3, value);
            ps.executeUpdate();
        }
    }

    private static void insertEvent(Connection c, long banId, String eventType, String actorType, String actorUsername, String actorServerKey, String details) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ban_events(ban_id, event_type, actor_type, actor_username, actor_server_key, details) VALUES(?,?,?,?,?,?)"
        )) {
            ps.setLong(1, banId);
            ps.setString(2, eventType);
            ps.setString(3, actorType);
            ps.setString(4, actorUsername);
            ps.setString(5, actorServerKey);
            ps.setString(6, details);
            ps.executeUpdate();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        return (v == null || v.isNull()) ? null : v.asText(null);
    }

    private static Long longOrNull(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return null;
        return v.asLong();
    }
}