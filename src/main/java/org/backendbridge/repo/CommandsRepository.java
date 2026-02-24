package org.backendbridge.repo;

import org.backendbridge.Db;
import org.backendbridge.Json;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.Locale;

/**
 * Server command queue.
 *
 * <p>Model:</p>
 * - Game server polls {@code GET /api/server/commands/poll?serverKey=...&sinceId=...}
 * - Backend returns commands where acknowledged_at is NULL and id > sinceId
 * - Game server acknowledges via {@code POST /api/server/commands/ack}
 *
 * <p>Client response contract (important):</p>
 * The client expects ONLY:
 * - serverTime
 * - commands
 *
 * (No "serverKey" field in the JSON response.)
 */
public final class CommandsRepository {

    private final Db db;

    public CommandsRepository(Db db) {
        this.db = db;
    }

    /**
     * Returns JSON payload for open commands (not acknowledged yet).
     *
     * Response:
     * {
     *   "serverTime": "ISO-8601",
     *   "commands": [
     *     { "id": 1, "type": "SHUTDOWN", "createdAt": "...", "payloadJson": "{...}" }
     *   ]
     * }
     */
    public String pollOpenCommandsJson(String serverKey, long sinceId, int limit) throws Exception {
        String sk = (serverKey == null) ? "" : serverKey.trim();
        if (sk.isBlank()) throw new IllegalArgumentException("serverKey missing");

        int lim = Math.max(1, Math.min(limit, 200));

        String sql =
                "SELECT id, cmd_type, payload_json, created_at " +
                        "FROM server_commands " +
                        "WHERE server_key=? AND acknowledged_at IS NULL AND id > ? " +
                        "ORDER BY id ASC LIMIT " + lim;

        StringBuilder out = new StringBuilder(16_000);
        out.append("{\"serverTime\":").append(Json.js(Instant.now().toString())).append(",\"commands\":[");

        boolean first = true;
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sk);
            ps.setLong(2, Math.max(0L, sinceId));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if (!first) out.append(',');
                    first = false;

                    long id = rs.getLong("id");
                    String type = rs.getString("cmd_type");
                    String payload = rs.getString("payload_json");
                    String createdAt = rs.getTimestamp("created_at").toInstant().toString();

                    out.append("{")
                            .append("\"id\":").append(id).append(',')
                            .append("\"type\":").append(Json.js(type == null ? "" : type.toUpperCase(Locale.ROOT))).append(',')
                            .append("\"createdAt\":").append(Json.js(createdAt)).append(',')
                            .append("\"payloadJson\":").append(payload == null || payload.isBlank() ? "null" : Json.js(payload))
                            .append("}");
                }
            }
        }

        out.append("]}");
        return out.toString();
    }

    /**
     * Marks a command as acknowledged.
     */
    public void ackCommand(String serverKey, long id) throws Exception {
        String sk = (serverKey == null) ? "" : serverKey.trim();
        if (sk.isBlank()) throw new IllegalArgumentException("serverKey missing");
        if (id <= 0) throw new IllegalArgumentException("id missing");

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE server_commands SET acknowledged_at=CURRENT_TIMESTAMP(3) " +
                             "WHERE server_key=? AND id=? AND acknowledged_at IS NULL"
             )) {
            ps.setString(1, sk);
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    /**
     * Optional helper to enqueue a command (useful for future Admin UI actions).
     */
    public long enqueueCommand(String serverKey, String cmdType, String payloadJsonOrNull) throws Exception {
        String sk = (serverKey == null) ? "" : serverKey.trim();
        if (sk.isBlank()) throw new IllegalArgumentException("serverKey missing");

        String ct = (cmdType == null) ? "" : cmdType.trim().toUpperCase(Locale.ROOT);
        if (ct.isBlank()) throw new IllegalArgumentException("cmdType missing");

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO server_commands(server_key, cmd_type, payload_json) VALUES(?, ?, ?)",
                     PreparedStatement.RETURN_GENERATED_KEYS
             )) {
            ps.setString(1, sk);
            ps.setString(2, ct);
            ps.setString(3, payloadJsonOrNull);

            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                return keys.next() ? keys.getLong(1) : -1L;
            }
        }
    }
}