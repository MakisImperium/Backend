--- BackendBridge schema (RESET: DROP + CREATE)
-- MySQL / MariaDB

DROP DATABASE IF EXISTS banbridge;
CREATE DATABASE banbridge
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE banbridge;

-- =========================================================
-- Core
-- =========================================================

CREATE TABLE servers (
                         server_key   VARCHAR(64)  NOT NULL,
                         server_token VARCHAR(255) NOT NULL,
                         created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                         last_seen_at TIMESTAMP(3) NULL,
                         PRIMARY KEY (server_key),
                         KEY idx_servers_last_seen (last_seen_at)
);

CREATE TABLE players (
                         xuid              VARCHAR(64)  NOT NULL,
                         last_name         VARCHAR(32)  NULL,
                         first_seen_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                         last_seen_at      TIMESTAMP(3) NULL,

                         online            TINYINT(1)   NOT NULL DEFAULT 0,
                         online_updated_at TIMESTAMP(3) NULL,

                         last_ip           VARCHAR(45)  NULL,
                         last_hwid         VARCHAR(128) NULL,

                         PRIMARY KEY (xuid),
                         KEY idx_players_last_seen (last_seen_at),
                         KEY idx_players_online (online, online_updated_at),
                         KEY idx_players_last_ip (last_ip),
                         KEY idx_players_last_hwid (last_hwid)
);

CREATE TABLE player_stats (
                              xuid             VARCHAR(64)  NOT NULL,
                              playtime_seconds BIGINT       NOT NULL DEFAULT 0,
                              kills            BIGINT       NOT NULL DEFAULT 0,
                              deaths           BIGINT       NOT NULL DEFAULT 0,
                              updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
                              PRIMARY KEY (xuid),
                              CONSTRAINT fk_player_stats_player
                                  FOREIGN KEY (xuid) REFERENCES players(xuid)
                                      ON DELETE CASCADE
);

-- =========================================================
-- Bans
-- =========================================================

CREATE TABLE bans (
                      ban_id           BIGINT NOT NULL AUTO_INCREMENT,

                      xuid             VARCHAR(64) NOT NULL,
                      reason           VARCHAR(255) NULL,

                      created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                      expires_at       TIMESTAMP(3) NULL,
                      revoked_at       TIMESTAMP(3) NULL,
                      updated_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

                      actor_type       ENUM('WEB','SERVER') NOT NULL DEFAULT 'WEB',
                      actor_username   VARCHAR(64) NULL,
                      actor_server_key VARCHAR(64) NULL,

                      PRIMARY KEY (ban_id),

                      KEY idx_bans_updated_at (updated_at),
                      KEY idx_bans_xuid (xuid),
                      KEY idx_bans_revoked_at (revoked_at),

                      CONSTRAINT fk_bans_player
                          FOREIGN KEY (xuid) REFERENCES players(xuid)
                              ON DELETE CASCADE,

                      CONSTRAINT fk_bans_actor_server
                          FOREIGN KEY (actor_server_key) REFERENCES servers(server_key)
                              ON DELETE SET NULL
);

CREATE TABLE ban_targets (
                             ban_id       BIGINT NOT NULL,
                             target_type  ENUM('XUID','IP','HWID') NOT NULL,
                             target_value VARCHAR(128) NOT NULL,

                             PRIMARY KEY (ban_id, target_type, target_value),
                             KEY idx_ban_targets_lookup (target_type, target_value),

                             CONSTRAINT fk_ban_targets_ban
                                 FOREIGN KEY (ban_id) REFERENCES bans(ban_id)
                                     ON DELETE CASCADE
);

CREATE TABLE ban_events (
                            id              BIGINT NOT NULL AUTO_INCREMENT,
                            ban_id           BIGINT NOT NULL,
                            event_type       ENUM('CREATED','ENFORCED','REVOKED') NOT NULL,

                            actor_type       ENUM('WEB','SERVER') NOT NULL,
                            actor_username   VARCHAR(64) NULL,
                            actor_server_key VARCHAR(64) NULL,

                            created_at       TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                            details          TEXT NULL,

                            PRIMARY KEY (id),
                            KEY idx_ban_events_ban_id (ban_id),
                            KEY idx_ban_events_created_at (created_at),

                            CONSTRAINT fk_ban_events_ban
                                FOREIGN KEY (ban_id) REFERENCES bans(ban_id)
                                    ON DELETE CASCADE,

                            CONSTRAINT fk_ban_events_server
                                FOREIGN KEY (actor_server_key) REFERENCES servers(server_key)
                                    ON DELETE SET NULL
);

-- =========================================================
-- Web / Admin: Roles + Users (1 role per user)
-- =========================================================

CREATE TABLE web_roles (
                           id           BIGINT NOT NULL AUTO_INCREMENT,
                           role_key     VARCHAR(64) NOT NULL,
                           display_name VARCHAR(64) NOT NULL,
                           created_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                           PRIMARY KEY (id),
                           UNIQUE KEY uq_web_roles_key (role_key)
);

CREATE TABLE web_users (
                           id            BIGINT NOT NULL AUTO_INCREMENT,
                           username      VARCHAR(64)  NOT NULL,
                           password_hash VARCHAR(255) NOT NULL,

                           role_id       BIGINT       NOT NULL,

                           is_protected  TINYINT(1)   NOT NULL DEFAULT 0,
                           disabled_at   TIMESTAMP(3) NULL,
                           created_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                           updated_at    TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

                           PRIMARY KEY (id),
                           UNIQUE KEY uq_web_users_username (username),
                           KEY idx_web_users_role (role_id),

                           CONSTRAINT fk_web_users_role
                               FOREIGN KEY (role_id) REFERENCES web_roles(id)
                                   ON DELETE RESTRICT
);

CREATE TABLE web_permissions (
                                 id          BIGINT NOT NULL AUTO_INCREMENT,
                                 perm_key    VARCHAR(96) NOT NULL,
                                 description VARCHAR(255) NULL,
                                 created_at  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 PRIMARY KEY (id),
                                 UNIQUE KEY uq_web_permissions_key (perm_key)
);

CREATE TABLE web_role_permissions (
                                      role_id BIGINT NOT NULL,
                                      perm_id BIGINT NOT NULL,
                                      PRIMARY KEY (role_id, perm_id),
                                      CONSTRAINT fk_roleperm_role FOREIGN KEY (role_id) REFERENCES web_roles(id) ON DELETE CASCADE,
                                      CONSTRAINT fk_roleperm_perm FOREIGN KEY (perm_id) REFERENCES web_permissions(id) ON DELETE CASCADE
);

-- =========================================================
-- Admin audit log (release-grade observability)
-- =========================================================

CREATE TABLE IF NOT EXISTS admin_audit_log (
                                               id            BIGINT NOT NULL AUTO_INCREMENT,
                                               actor_username VARCHAR(64) NULL,
    action_key     VARCHAR(96) NOT NULL,
    details        TEXT NULL,
    created_at     TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_audit_created_at (created_at),
    KEY idx_audit_actor (actor_username, created_at)
    );


INSERT IGNORE INTO web_roles(role_key, display_name) VALUES
  ('admin', 'Admin'),
  ('supporter', 'Supporter');

INSERT IGNORE INTO web_permissions(perm_key, description) VALUES
  ('users.manage', 'Can manage web users'),
  ('roles.manage', 'Can manage roles and permissions'),
  ('stats.view', 'Can view server stats'),
  ('players.view', 'Can view players'),
  ('players.presence', 'Can view online/offline presence'),
  ('bans.view', 'Can view bans'),
  ('bans.unban', 'Can unban players'),
  ('bans.ban', 'Can ban players'),
  ('audit.view', 'Can view admin audit log'),
  ('server.commands', 'Can send server commands (shutdown/refresh)');


INSERT IGNORE INTO web_role_permissions(role_id, perm_id)
SELECT r.id, p.id
FROM web_roles r
         JOIN web_permissions p
WHERE r.role_key = 'admin';

INSERT IGNORE INTO web_role_permissions(role_id, perm_id)
SELECT r.id, p.id
FROM web_roles r
         JOIN web_permissions p
WHERE r.role_key = 'supporter'
  AND p.perm_key IN ('players.view','players.presence','bans.view','bans.unban','stats.view');

-- =========================================================
-- Server metrics (latest snapshot + full history)
-- =========================================================

CREATE TABLE server_metrics_latest (
                                       server_key      VARCHAR(64)  NOT NULL,
                                       updated_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

                                       ram_used_mb     INT NULL,
                                       ram_max_mb      INT NULL,
                                       cpu_load        DOUBLE NULL,

                                       players_online  INT NULL,
                                       players_max     INT NULL,
                                       tps             DOUBLE NULL,

                                       rx_kbps         DOUBLE NULL,
                                       tx_kbps         DOUBLE NULL,

                                       PRIMARY KEY (server_key),
                                       CONSTRAINT fk_metrics_server
                                           FOREIGN KEY (server_key) REFERENCES servers(server_key)
                                               ON DELETE CASCADE
);

CREATE TABLE server_metrics (
                                id              BIGINT NOT NULL AUTO_INCREMENT,
                                server_key      VARCHAR(64)  NOT NULL,
                                created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

                                ram_used_mb     INT NULL,
                                ram_max_mb      INT NULL,
                                cpu_load        DOUBLE NULL,

                                players_online  INT NULL,
                                players_max     INT NULL,
                                tps             DOUBLE NULL,

                                rx_kbps         DOUBLE NULL,
                                tx_kbps         DOUBLE NULL,

                                PRIMARY KEY (id),
                                KEY idx_metrics_server_time (server_key, created_at),
                                CONSTRAINT fk_metrics_ts_server
                                    FOREIGN KEY (server_key) REFERENCES servers(server_key)
                                        ON DELETE CASCADE
);

-- =========================================================
-- Backend -> Client commands (client polls)
-- =========================================================

CREATE TABLE server_commands (
                                 id              BIGINT NOT NULL AUTO_INCREMENT,
                                 server_key      VARCHAR(64) NOT NULL,
                                 cmd_type        ENUM('SHUTDOWN','REFRESH_BANS') NOT NULL,
                                 payload_json    TEXT NULL,
                                 created_at      TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
                                 acknowledged_at TIMESTAMP(3) NULL,

                                 PRIMARY KEY (id),
                                 KEY idx_cmd_server_created (server_key, created_at),
                                 KEY idx_cmd_server_ack (server_key, acknowledged_at, id),

                                 CONSTRAINT fk_cmd_server
                                     FOREIGN KEY (server_key) REFERENCES servers(server_key)
                                         ON DELETE CASCADE
);