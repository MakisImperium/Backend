package org.backendbridge.repo;

import com.fasterxml.jackson.databind.JsonNode;
import org.backendbridge.Db;
import org.backendbridge.LiveBus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Stores server monitoring metrics:
 * - latest snapshot in server_metrics_latest
 * - full time series in server_metrics
 *
 * <p>On ingest, publishes LiveBus invalidate("stats") to update the Admin UI.</p>
 */
public final class MetricsRepository {

    private final Db db;

    public MetricsRepository(Db db) {
        this.db = db;
    }

    public void ingest(String serverKey, JsonNode body) throws Exception {
        if (serverKey == null || serverKey.isBlank()) throw new IllegalArgumentException("serverKey missing");

        Integer ramUsed = intOrNull(body, "ramUsedMb");
        Integer ramMax = intOrNull(body, "ramMaxMb");
        Double cpu = dblOrNull(body, "cpuLoad");

        Integer pOn = intOrNull(body, "playersOnline");
        Integer pMax = intOrNull(body, "playersMax");
        Double tps = dblOrNull(body, "tps");

        Double rx = dblOrNull(body, "rxKbps");
        Double tx = dblOrNull(body, "txKbps");

        // -------- sanitize client data (treat negatives as "unknown") --------
        if (ramUsed != null && ramUsed < 0) ramUsed = null;
        if (ramMax != null && ramMax < 0) ramMax = null;
        if (ramUsed != null && ramMax != null && ramUsed > ramMax) ramUsed = ramMax;

        if (cpu != null && (cpu < 0.0 || cpu > 1.5)) cpu = null;

        if (pOn != null && pOn < 0) pOn = null;
        if (pMax != null && pMax < 0) pMax = null;
        if (pOn != null && pMax != null && pOn > pMax) pOn = pMax;

        if (tps != null && tps < 0.0) tps = null;
        if (rx != null && rx < 0.0) rx = null;
        if (tx != null && tx < 0.0) tx = null;
        // -------------------------------------------------------------------

        try (Connection c = db.getConnection()) {
            // latest snapshot
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO server_metrics_latest(" +
                            "server_key, ram_used_mb, ram_max_mb, cpu_load, players_online, players_max, tps, rx_kbps, tx_kbps" +
                            ") VALUES(?,?,?,?,?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "updated_at=CURRENT_TIMESTAMP(3), " +
                            "ram_used_mb=VALUES(ram_used_mb), " +
                            "ram_max_mb=VALUES(ram_max_mb), " +
                            "cpu_load=VALUES(cpu_load), " +
                            "players_online=VALUES(players_online), " +
                            "players_max=VALUES(players_max), " +
                            "tps=VALUES(tps), " +
                            "rx_kbps=VALUES(rx_kbps), " +
                            "tx_kbps=VALUES(tx_kbps)"
            )) {
                ps.setString(1, serverKey);
                setInt(ps, 2, ramUsed);
                setInt(ps, 3, ramMax);
                setDouble(ps, 4, cpu);
                setInt(ps, 5, pOn);
                setInt(ps, 6, pMax);
                setDouble(ps, 7, tps);
                setDouble(ps, 8, rx);
                setDouble(ps, 9, tx);
                ps.executeUpdate();
            }

            // full history
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO server_metrics(" +
                            "server_key, ram_used_mb, ram_max_mb, cpu_load, players_online, players_max, tps, rx_kbps, tx_kbps" +
                            ") VALUES(?,?,?,?,?,?,?,?,?)"
            )) {
                ps.setString(1, serverKey);
                setInt(ps, 2, ramUsed);
                setInt(ps, 3, ramMax);
                setDouble(ps, 4, cpu);
                setInt(ps, 5, pOn);
                setInt(ps, 6, pMax);
                setDouble(ps, 7, tps);
                setDouble(ps, 8, rx);
                setDouble(ps, 9, tx);
                ps.executeUpdate();
            }
        }

        LiveBus.publishInvalidate("stats");
    }

    public Metrics loadLatest(String serverKey) throws Exception {
        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT updated_at, ram_used_mb, ram_max_mb, cpu_load, players_online, players_max, tps, rx_kbps, tx_kbps " +
                             "FROM server_metrics_latest WHERE server_key=? LIMIT 1"
             )) {
            ps.setString(1, serverKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Metrics(
                        rs.getTimestamp("updated_at").toInstant().toString(),
                        (Integer) rs.getObject("ram_used_mb"),
                        (Integer) rs.getObject("ram_max_mb"),
                        (Double) rs.getObject("cpu_load"),
                        (Integer) rs.getObject("players_online"),
                        (Integer) rs.getObject("players_max"),
                        (Double) rs.getObject("tps"),
                        (Double) rs.getObject("rx_kbps"),
                        (Double) rs.getObject("tx_kbps")
                );
            }
        }
    }

    public List<MetricPoint> loadHistory(String serverKey, int limit) throws Exception {
        int lim = Math.max(10, Math.min(limit, 2000));
        List<MetricPoint> out = new ArrayList<>(lim);

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT created_at, players_online, tps, cpu_load, ram_used_mb " +
                             "FROM server_metrics WHERE server_key=? " +
                             "ORDER BY created_at DESC LIMIT " + lim
             )) {
            ps.setString(1, serverKey);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new MetricPoint(
                            rs.getTimestamp("created_at").toInstant().toString(),
                            (Integer) rs.getObject("players_online"),
                            (Double) rs.getObject("tps"),
                            (Double) rs.getObject("cpu_load"),
                            (Integer) rs.getObject("ram_used_mb")
                    ));
                }
            }
        }

        for (int i = 0, j = out.size() - 1; i < j; i++, j--) {
            MetricPoint tmp = out.get(i);
            out.set(i, out.get(j));
            out.set(j, tmp);
        }
        return out;
    }

    private static Integer intOrNull(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return null;
        return v.asInt();
    }

    private static Double dblOrNull(JsonNode n, String field) {
        JsonNode v = n == null ? null : n.get(field);
        if (v == null || v.isNull()) return null;
        return v.asDouble();
    }

    private static void setInt(PreparedStatement ps, int idx, Integer v) throws Exception {
        if (v == null) ps.setObject(idx, null);
        else ps.setInt(idx, v);
    }

    private static void setDouble(PreparedStatement ps, int idx, Double v) throws Exception {
        if (v == null) ps.setObject(idx, null);
        else ps.setDouble(idx, v);
    }

    public record Metrics(
            String updatedAtIso,
            Integer ramUsedMb,
            Integer ramMaxMb,
            Double cpuLoad,
            Integer playersOnline,
            Integer playersMax,
            Double tps,
            Double rxKbps,
            Double txKbps
    ) {}

    public record MetricPoint(
            String atIso,
            Integer playersOnline,
            Double tps,
            Double cpuLoad,
            Integer ramUsedMb
    ) {}
}