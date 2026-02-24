package org.backendbridge.repo;

import com.fasterxml.jackson.databind.JsonNode;
import org.backendbridge.Db;
import org.backendbridge.LiveBus;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Persists player stats deltas reported by the game server.
 *
 * <p>Input model:</p>
 * The server POSTs a JSON body containing a "players" array (see HttpApiServer),
 * each entry containing:
 * <ul>
 *   <li>xuid (string, required)</li>
 *   <li>name (string, optional)</li>
 *   <li>playtimeDeltaSeconds (number, optional)</li>
 *   <li>killsDelta (number, optional)</li>
 *   <li>deathsDelta (number, optional)</li>
 * </ul>
 *
 * <p>Behavior:</p>
 * - Upserts player basic info into {@code players}
 * - Adds deltas into {@code player_stats} using an atomic upsert increment
 * - Publishes a LiveBus invalidation for "players" so the Admin UI can refresh
 */
public final class StatsRepository {

    private final Db db;

    public StatsRepository(Db db) {
        this.db = db;
    }

    /**
     * Writes stats deltas for a batch of players in a single transaction.
     */
    public void persistStatsPlayersArray(JsonNode playersArray) throws Exception {
        if (playersArray == null || !playersArray.isArray()) return;

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (JsonNode p : playersArray) {
                    String xuid = text(p, "xuid");
                    if (xuid == null || xuid.isBlank()) continue;

                    String name = text(p, "name");
                    long pt = longVal(p, "playtimeDeltaSeconds");
                    long k = longVal(p, "killsDelta");
                    long d = longVal(p, "deathsDelta");

                    // Defensive clamping (server deltas should never be negative)
                    if (pt < 0) pt = 0;
                    if (k < 0) k = 0;
                    if (d < 0) d = 0;

                    upsertPlayer(c, xuid, name);
                    upsertStatsAddByXuid(c, xuid, pt, k, d);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }

        // Live update Admin UI
        LiveBus.publishInvalidate("players");
    }

    /**
     * Updates player metadata and last seen timestamp.
     */
    private void upsertPlayer(Connection c, String xuid, String name) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO players(xuid, last_name, last_seen_at) VALUES(?, ?, CURRENT_TIMESTAMP(3)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "last_name=VALUES(last_name), " +
                        "last_seen_at=CURRENT_TIMESTAMP(3)"
        )) {
            ps.setString(1, xuid);
            ps.setString(2, (name == null || name.isBlank()) ? "Unknown" : name);
            ps.executeUpdate();
        }
    }

    /**
     * Adds deltas to the player's aggregated stats row (atomic upsert).
     */
    private void upsertStatsAddByXuid(Connection c, String xuid, long pt, long k, long d) throws Exception {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO player_stats(xuid, playtime_seconds, kills, deaths, updated_at) " +
                        "VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP(3)) " +
                        "ON DUPLICATE KEY UPDATE " +
                        "playtime_seconds = player_stats.playtime_seconds + VALUES(playtime_seconds), " +
                        "kills = player_stats.kills + VALUES(kills), " +
                        "deaths = player_stats.deaths + VALUES(deaths), " +
                        "updated_at = CURRENT_TIMESTAMP(3)"
        )) {
            ps.setString(1, xuid);
            ps.setLong(2, pt);
            ps.setLong(3, k);
            ps.setLong(4, d);
            ps.executeUpdate();
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = (n == null) ? null : n.get(field);
        return (v == null || v.isNull()) ? null : v.asText(null);
    }

    private static long longVal(JsonNode n, String field) {
        JsonNode v = (n == null) ? null : n.get(field);
        if (v == null || v.isNull()) return 0L;
        return v.asLong(0L);
    }
}