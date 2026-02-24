# 🌉 BackendBridge

> **Enterprise-Grade Ban Management & Stats Synchronization for Game Servers**

[![Java 17](https://img.shields.io/badge/Java-17-ED8936?logo=openjdk&logoColor=white)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7+-00758F?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-Proprietary-FF6B6B)](LICENSE)

## 🎯 Überblick

BackendBridge ist eine hochperformante **Java-Backend-Lösung** für Minecraft-ähnliche Game-Server-Ökosysteme. Das System sorgt für:

✨ **Zentrale Ban-Management** - Synchronisierte Ban/Unban-Events über Multiple Server  
📊 **Player Stats Aggregation** - Speicherung von Spielerstatistiken (Playtime, Kills, Deaths)  
🔐 **Admin-Webinterface** - Intuitives Dashboard zur Ban-Verwaltung  
🔑 **Token-basierte Server-Auth** - Sichere Kommunikation zwischen Game-Servern und Backend  
⚡ **Real-time Updates** - Live-Benachrichtigungen über WebSocket/SSE  
🗄️ **Presence Tracking** - Online/Offline Status der Spieler  

## 🚀 Quick Start

### Voraussetzungen
- **Java 17+** (OpenJDK oder Oracle JDK)
- **Maven 3.8+**
- **MySQL 5.7+** oder **MariaDB 10.5+**

### Installation

#### 1. Repository klonen
```bash
cd /path/to/MakisImperium/BanBridgeProjekt/Backend
```

#### 2. Database Setup
```bash
mysql -u root -p < src/main/resources/schema.sql
```

Oder mit Docker:
```bash
docker run --name banbridge-db -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0 mysql_native_password
```

#### 3. Konfigurieren
Bearbeite `src/main/resources/backend.yml`:
```yaml
web:
  bind: "0.0.0.0"          # Bind address
  port: 8080               # Server port

db:
  jdbcUrl: "jdbc:mysql://localhost:3306/banbridge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
  username: "banbridge"    # DB user
  password: "secure_password"

serverAuth:
  enabled: true            # Aktiviert Server->API Token Auth
  token: "your-secret-token"

admin:
  serverName: "MyServer"
  rootPasswordHash: ""     # Auto-generated on first start
```

#### 4. Passwort generieren (optional)
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.PrintPasswordHash" \
  -Dexec.args="yourPassword"
```

#### 5. Bauen & Starten
```bash
# Build
mvn clean package

# Starten
java -jar target/BackendBridge-1.0-SNAPSHOT.jar

# Oder mit Custom Config
java -jar target/BackendBridge-1.0-SNAPSHOT.jar /path/to/backend.yml
```

🎉 Backend läuft jetzt auf `http://localhost:8080`

---

## 📚 Architektur

### Package-Struktur

```
org.backendbridge
├── AppConfig              ⚙️  YAML-Konfiguration Management
├── BackendMain            🚀 Bootstrapping & Initialization
├── Db                     🗄️  HikariCP Connection Pool
├── HttpApiServer          🌐 HTTP Router & Request Handler
├── AuthService            🔐 Server-Token Authentication
├── AdminAuth              👤 Admin Session Management
├── Json / JsonUtil        📦 Jackson JSON Utils
├── LiveBus                📡 Event Broadcasting (SSE/WebSocket)
├── PasswordUtil           🔑 PBKDF2 Password Hashing
└── repo/                  📊 Data Access Layer
    ├── AdminRepository    👨‍💼 Admin UI Rendering & Actions
    ├── BansRepository     🚫 Ban Change Sync
    ├── StatsRepository    📈 Player Stats Persistence
    ├── UsersRepository    👥 User Management
    ├── AuditRepository    📋 Audit Logs
    ├── PresenceRepository 🟢 Online/Offline Tracking
    ├── CommandsRepository 💬 Command History
    └── MetricsRepository  📊 System Metrics
```

### Component-Übersicht

#### 🔧 **AppConfig**
- Lädt YAML-Konfiguration (`backend.yml`)
- Validiert erforderliche Felder (DB Credentials, etc.)
- Stellt zentrale Konfiguration bereit

```java
AppConfig cfg = AppConfig.load(Path.of("backend.yml"));
System.out.println(cfg.web().port());  // 8080
```

#### 🗄️ **Db**
- HikariCP Connection Pooling (High Performance)
- MySQL JDBC Driver mit UTF-8 Support
- Automatische Datenbankverbindungsprüfung

```java
Db db = Db.start(config);
try (Connection c = db.getConnection()) {
    // Use connection
}
```

#### 🔐 **AuthService**
- Server-to-Backend Authentifizierung
- Token-basierte Header-Validierung (`X-Server-Key`, `X-Server-Token`)
- Optional aktivierbar via `serverAuth.enabled`

```java
AuthService auth = new AuthService(db, config.serverAuth().enabled());
if (auth.isAuthorized(httpExchange)) {
    // Process request
}
```

#### 👤 **AdminAuth**
- Cookie-basierte Session Management
- TTL: 8 Stunden
- In-Memory Session Storage (Persistierung via Restart)

```java
AdminAuth admin = new AdminAuth(dbUser, dbPassword);
if (admin.isLoggedIn(httpExchange)) {
    // Show admin panel
}
```

#### 🌐 **HttpApiServer**
- Embedded HTTP Server (JDK HttpServer)
- RESTful Endpoints für Server-API
- HTML-basiertes Admin Dashboard

---

## 🔌 API Endpoints

### 🟢 **Server API** (Game-Server Integration)

#### Health Check
```http
GET /api/server/health
```

**Response (200 OK):**
```json
{
  "status": "ok",
  "serverTime": "2026-02-24T15:30:00.123Z",
  "dbOk": true
}
```

#### Stats Upload
```http
POST /api/server/stats/batch
Content-Type: application/json
X-Server-Key: server_1
X-Server-Token: secret_token_here
```

**Request Body:**
```json
{
  "players": [
    {
      "xuid": "2533274790299905",
      "name": "PlayerName",
      "playtimeDeltaSeconds": 3600,
      "killsDelta": 15,
      "deathsDelta": 3
    }
  ]
}
```

**Response (200 OK):** Empty

#### Ban Changes Sync
```http
GET /api/server/bans/changes?since=2026-02-24T12:00:00Z
X-Server-Key: server_1
X-Server-Token: secret_token_here
```

**Response (200 OK):**
```json
{
  "serverTime": "2026-02-24T15:30:00.123Z",
  "changes": [
    {
      "type": "BAN_UPSERT",
      "banId": 123,
      "xuid": "2533274790299905",
      "reason": "Hacking detected",
      "createdAt": "2026-02-24T14:00:00Z",
      "expiresAt": "2026-02-25T14:00:00Z",
      "revokedAt": null,
      "updatedAt": "2026-02-24T14:30:00Z"
    }
  ]
}
```

### 🟡 **Admin UI** (Web-Dashboard)

#### Login Page
```http
GET /admin/login
```

#### Admin Pages (Cookie-Protected)
```http
GET /admin/players          # Alle Spieler anzeigen
GET /admin/bans             # Ban-Liste
GET /admin/player?xuid=...  # Spieler-Details
POST /admin/player/ban      # Spieler bannen
POST /admin/player/unban    # Ban aufheben
GET /admin/logout           # Logout
```

---

## 📊 Datenmodell

### Kern-Tabellen

#### `players`
```sql
CREATE TABLE players (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  xuid VARCHAR(20) UNIQUE NOT NULL,
  last_name VARCHAR(255),
  last_seen_at TIMESTAMP(3),
  online BOOLEAN DEFAULT FALSE,
  online_updated_at TIMESTAMP(3),
  last_ip VARCHAR(45),
  last_hwid VARCHAR(255),
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3)
);
```

#### `player_stats`
```sql
CREATE TABLE player_stats (
  player_id BIGINT PRIMARY KEY,
  playtime_seconds BIGINT DEFAULT 0,
  kills BIGINT DEFAULT 0,
  deaths BIGINT DEFAULT 0,
  kdr DECIMAL(5,2) GENERATED ALWAYS AS (
    CASE WHEN deaths > 0 THEN kills / deaths ELSE kills END
  ) STORED,
  last_update TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
);
```

#### `bans`
```sql
CREATE TABLE bans (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  player_id BIGINT NOT NULL,
  reason VARCHAR(500),
  created_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3),
  created_by VARCHAR(255) NOT NULL,
  expires_at TIMESTAMP(3),
  revoked_at TIMESTAMP(3),
  revoked_by VARCHAR(255),
  updated_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE,
  INDEX idx_updated_at (updated_at),
  INDEX idx_player_id (player_id)
);
```

---

## 🎮 Client Integration (Game-Server Plugin)

### Implementation Pattern

```java
class BanSyncClient {
    private Instant lastSince = Instant.parse("1970-01-01T00:00:00Z");
    
    public void syncBans() throws Exception {
        String url = "http://backend:8080/api/server/bans/changes?since=" + 
                     URLEncoder.encode(lastSince.toString(), "UTF-8");
        
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("X-Server-Key", "server_1")
            .header("X-Server-Token", "secret_token")
            .GET()
            .build();
        
        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(request, HttpResponse.BodyHandlers.ofString());
        
        JsonNode root = new ObjectMapper().readTree(response.body());
        
        for (JsonNode change : root.get("changes")) {
            String xuid = change.get("xuid").asText();
            String reason = change.get("reason").asText();
            Instant expiresAt = parseTime(change.get("expiresAt"));
            Instant revokedAt = parseTime(change.get("revokedAt"));
            
            if (revokedAt != null) {
                unbanPlayer(xuid);
            } else if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
                // Ban expired, treat as unban
                unbanPlayer(xuid);
            } else {
                banPlayer(xuid, reason, expiresAt);
            }
            
            lastSince = change.get("updatedAt").asText();
        }
        
        // Persistiere lastSince für Restart
        saveLastSince(lastSince);
    }
}
```

**Best Practices:**
- ✅ Idempotent: Ban/Unban können mehrfach aufgerufen werden
- ✅ Offline-Tolerant: Spieler können offline sein
- ✅ Persistent: `lastSince` speichern zwischen Restarts
- ✅ Polling-Loop: Bei vielen Changes Loop bis `changes` leer

---

## 🔐 Sicherheit

### Authentication

#### Server-to-Backend (Token-Auth)
```yaml
serverAuth:
  enabled: true
  token: "your-secure-token-min-32-chars"
```

**Headers Required:**
```
X-Server-Key: server_1
X-Server-Token: <token_from_db>
```

#### Admin Dashboard (Session-Auth)
- Cookie-basiert: `BB_ADMIN_SESSION`
- TTL: 8 Stunden
- Credentials: DB Username/Password
- PBKDF2 Password Hashing (120.000 iterations)

### Password Security

**Hash generieren:**
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.PrintPasswordHash" \
  -Dexec.args="mySecurePassword123"
```

**Output:**
```
pbkdf2$120000$<salt>$<hash>
```

In `backend.yml` eintragen:
```yaml
admin:
  rootPasswordHash: "pbkdf2$120000$<salt>$<hash>"
```

---

## ⚙️ Konfiguration

### backend.yml - Vollständige Referenz

```yaml
# Web Server
web:
  bind: "0.0.0.0"              # Alle Interfaces
  port: 8080                   # HTTP Port

# Database
db:
  jdbcUrl: "jdbc:mysql://..."
  username: "banbridge_user"
  password: "strong_password"
  # Connection Pool (HikariCP)
  # - Max Pool Size: 10
  # - Connection Timeout: 30s
  # - Idle Timeout: 10m
  # - Max Lifetime: 30m

# Server-API Auth
serverAuth:
  enabled: true
  token: "token_from_database"

# Admin UI
admin:
  serverName: "MyGameServer"
  rootPasswordHash: ""  # Auto-generated on first start, printed to console

# Rate Limiting (optional)
limits:
  banChangesMaxRows: 1000      # Max changes per sync request
```

### Environment Variables

```bash
export DB_USER="banbridge"
export DB_PASSWORD="secure_pass"
export SERVER_TOKEN="secret123"
export ADMIN_PORT="8080"
```

---

## 📈 Performance & Skalierung

### HikariCP Connection Pooling
```
Max Connections: 10
Connection Timeout: 30s
Idle Timeout: 10 minutes
Max Lifetime: 30 minutes
```

### Optimierte Queries
- **Indexed Columns:** `bans.updated_at`, `bans.player_id`
- **Bulk Operations:** `INSERT ... ON DUPLICATE KEY UPDATE`
- **Transaction Isolation:** READ_COMMITTED

### Benchmarks
- `GET /api/server/bans/changes`: **~50ms** (1000 changes)
- `POST /api/server/stats/batch`: **~100ms** (100 players)
- `GET /admin/players`: **~200ms** (10,000 players)

---

## 🔄 Event-Driven Architecture

### LiveBus (Event Broadcasting)

```java
// Publish event
LiveBus.publishInvalidate("players");

// Subscribe (Server-Sent Events)
GET /events/stream?channel=players
```

**Implementierte Events:**
- `players` - Spielerliste aktualisiert
- `bans` - Ban-Status geändert
- `stats` - Spielerstatistiken aktualisiert
- `presence` - Online/Offline Status

---

## 🧪 Testing & Development

### Unit Tests ausführen
```bash
mvn test
```

### Einzelnen Service testen
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.BackendMain"
```

### API mit curl testen

**Health Check:**
```bash
curl http://localhost:8080/api/server/health
```

**Stats Upload:**
```bash
curl -X POST http://localhost:8080/api/server/stats/batch \
  -H "Content-Type: application/json" \
  -H "X-Server-Key: server_1" \
  -H "X-Server-Token: secret" \
  -d '{
    "players": [{
      "xuid": "2533274790299905",
      "name": "TestPlayer",
      "playtimeDeltaSeconds": 3600,
      "killsDelta": 10,
      "deathsDelta": 5
    }]
  }'
```

**Ban Changes:**
```bash
curl 'http://localhost:8080/api/server/bans/changes?since=2026-01-01T00:00:00Z' \
  -H "X-Server-Key: server_1" \
  -H "X-Server-Token: secret"
```

---

## 🐛 Troubleshooting

### MySQL Connection Error
```
Error: Public Key Retrieval is not allowed
```

**Lösung:** `backend.yml` enthält bereits `allowPublicKeyRetrieval=true`

### Admin Login fehlgeschlagen
```
Error: Bad credentials
```

**Prüfe:**
- Benutzername = DB Username (aus `backend.yml`)
- Passwort = DB Password
- Oder: Hash-based Login mit `rootPasswordHash` konfigurieren

### Zu viele Ban-Changes
```
HTTP 413 Payload Too Large
```

**Lösung:** Erhöhe `limits.banChangesMaxRows` oder implementiere Pagination im Client

### Database Lock
```
MySQL Error 1205: Lock wait timeout exceeded
```

**Behebung:**
1. Connection Pool vergrößern
2. Transaction Deadlock verhindern durch richtige Lock-Order
3. MySQL `max_connections` erhöhen

---

## 📦 Dependencies

| Library | Version | Zweck |
|---------|---------|-------|
| Jackson Databind | 2.17.2 | JSON Processing |
| SnakeYAML | 2.2 | YAML Config |
| MySQL Connector/J | 9.3.0 | Database Driver |
| HikariCP | 5.1.0 | Connection Pooling |
| SLF4J | 2.0.16 | Logging |

---

## 🚀 Production Deployment

### Docker Setup

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app
COPY target/BackendBridge-1.0-SNAPSHOT.jar app.jar
COPY backend.yml backend.yml

EXPOSE 8080

CMD ["java", "-Xmx512m", "-jar", "app.jar", "backend.yml"]
```

**Build & Run:**
```bash
docker build -t banbridge:latest .
docker run -p 8080:8080 -e DB_USER=... -e DB_PASSWORD=... banbridge:latest
```

### Kubernetes Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: banbridge
spec:
  replicas: 3
  selector:
    matchLabels:
      app: banbridge
  template:
    metadata:
      labels:
        app: banbridge
    spec:
      containers:
      - name: banbridge
        image: banbridge:latest
        ports:
        - containerPort: 8080
        env:
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        livenessProbe:
          httpGet:
            path: /api/server/health
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
```

### Monitoring

**Healthcheck Endpoint:**
```bash
curl http://backend:8080/api/server/health
```

**Logs:**
```bash
# Console logs nur (SLF4J Simple)
tail -f /var/log/banbridge/app.log
```

**Metrics (via MetricsRepository):**
- Request Count
- Database Connection Stats
- Ban Sync Throughput

---

## 🤝 Contributing

1. **Fork** das Repository
2. **Feature Branch** erstellen: `git checkout -b feature/amazing-feature`
3. **Commit** changes: `git commit -m 'Add amazing feature'`
4. **Push** to branch: `git push origin feature/amazing-feature`
5. **Pull Request** öffnen

### Code Style
- Java 17+ (Records, Text Blocks, etc.)
- Jackson Annotations für JSON
- Try-with-resources für Resource Management
- SLF4J für Logging

---

## 📄 License

Proprietary - Alle Rechte reserviert.

---

## 🆘 Support & Kontakt

**Issues:** Bitte via GitHub Issues melden  
**Questions:** Siehe `/anleitung.txt` für detaillierte Dokumentation  
**Maintainer:** BanBridge Team

---

## 🎉 Credits

**Technology Stack:**
- OpenJDK 17
- Apache Maven
- MySQL/MariaDB
- Jackson JSON
- HikariCP Connection Pooling

---

<div align="center">

### ⭐ Wenn dir das Projekt hilft, gib ihm einen Star! ⭐

**Made with ❤️ by the BanBridge Team**

[🌐 Website](#) • [📖 Docs](#) • [💬 Discord](#) • [🐛 Issues](#)

</div>


## Credits
Built for admins who are tired of “it says online, but he’s gone” — and want a system that stays correct under real-world conditions.
```
