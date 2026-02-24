package org.backendbridge.repo;

import org.backendbridge.Db;

import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Persists admin audit events.
 *
 * <p>Audit logging is best-effort:</p>
 * <ul>
 *   <li>Must never break business actions.</li>
 *   <li>Must never store secrets (passwords/tokens).</li>
 * </ul>
 */
public final class AuditRepository {

    private final Db db;

    public AuditRepository(Db db) {
        this.db = db;
    }

    /**
     * Writes an audit entry (best-effort).
     *
     * @param actorUsername logged-in username (may be null)
     * @param actionKey stable action identifier, e.g. "users.create"
     * @param details short free-form info; do NOT include secrets
     */
    public void log(String actorUsername, String actionKey, String details) {
        String ak = actionKey == null ? "" : actionKey.trim();
        if (ak.isBlank()) return;

        try (Connection c = db.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO admin_audit_log(actor_username, action_key, details) VALUES(?, ?, ?)"
             )) {
            ps.setString(1, (actorUsername == null || actorUsername.isBlank()) ? null : actorUsername.trim());
            ps.setString(2, ak);
            ps.setString(3, (details == null || details.isBlank()) ? null : details);
            ps.executeUpdate();
        } catch (Exception ignored) {
            // Fail-open on purpose.
        }
    }
}
