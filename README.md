# 🌉 BackendBridge

> **Enterprise-Grade Ban Management & Stats Synchronization for Game Servers**

[![Java 17](https://img.shields.io/badge/Java-17-ED8936?logo=openjdk&logoColor=white)](https://openjdk.java.net/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-C71A36?logo=apachemaven&logoColor=white)](https://maven.apache.org/)
[![MySQL](https://img.shields.io/badge/MySQL-5.7+-00758F?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-Proprietary-FF6B6B)](LICENSE)

## 🎯 Overview

BackendBridge is a high-performance **Java backend solution** for Minecraft-like game server ecosystems. The system provides:

✨ **Centralized Ban Management** - Synchronized ban/unban events across multiple servers  
📊 **Player Stats Aggregation** - Store player statistics (playtime, kills, deaths)  
🔐 **Admin Web Interface** - Intuitive dashboard for ban management  
🔑 **Token-Based Server Auth** - Secure communication between game servers and backend  
⚡ **Real-Time Updates** - Live notifications via Server-Sent Events  
🟢 **Presence Tracking** - Online/offline player status synchronization  

---

## 🚀 Quick Start

### Prerequisites
- **Java 17+** (OpenJDK or Oracle JDK)
- **Maven 3.8+**
- **MySQL 5.7+** or **MariaDB 10.5+**

### Installation

#### 1. Navigate to Project
```bash
cd /path/to/MakisImperium/BanBridgeProjekt/Backend
```

#### 2. Database Setup
```bash
mysql -u root -p < src/main/resources/schema.sql
```

Or with Docker:
```bash
docker run --name banbridge-db -e MYSQL_ROOT_PASSWORD=root -p 3306:3306 mysql:8.0 mysql_native_password
```

#### 3. Configure `backend.yml`
Edit `src/main/resources/backend.yml`:
```yaml
web:
  bind: "0.0.0.0"
  port: 8080

db:
  jdbcUrl: "jdbc:mysql://localhost:3306/banbridge?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&useSSL=false&allowPublicKeyRetrieval=true"
  username: "banbridge"
  password: "secure_password"

serverAuth:
  enabled: true
  token: "your-secret-token"

admin:
  serverName: "MyServer"
  rootPasswordHash: ""
```

#### 4. Generate Password Hash (Optional)
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.PrintPasswordHash" \
  -Dexec.args="yourPassword"
```

#### 5. Build & Start
```bash
# Build
mvn clean package

# Start
java -jar target/BackendBridge-1.0-SNAPSHOT.jar

# Or with custom config
java -jar target/BackendBridge-1.0-SNAPSHOT.jar /path/to/backend.yml
```

🎉 Backend running at `http://localhost:8080`

---

## 📚 Architecture

### Package Structure

```
org.backendbridge
├── AppConfig              ⚙️  YAML Configuration Management
├── BackendMain            🚀 Bootstrapping & Initialization
├── Db                     🗄️  HikariCP Connection Pool
├── HttpApiServer          🌐 HTTP Router & Request Handler
├── AuthService            🔐 Server-Token Authentication
├── AdminAuth              👤 Admin Session Management
├── Json / JsonUtil        📦 Jackson JSON Utils
├── LiveBus                📡 Event Broadcasting (SSE)
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

### Core Components

#### 🔧 **AppConfig**
Loads YAML configuration (`backend.yml`), validates required fields, provides centralized access.

```java
AppConfig cfg = AppConfig.load(Path.of("backend.yml"));
System.out.println(cfg.web().port());  // 8080
```

#### 🗄️ **Db**
HikariCP connection pooling with MySQL JDBC driver and UTF-8 support.

```java
Db db = Db.start(config);
try (Connection c = db.getConnection()) {
    // Use connection
}
```

#### 🔐 **AuthService**
Server-to-Backend authentication via token headers (`X-Server-Key`, `X-Server-Token`).

```java
AuthService auth = new AuthService(db, config.serverAuth().enabled());
if (auth.isAuthorized(httpExchange)) {
    // Process request
}
```

#### 👤 **AdminAuth**
Cookie-based session management with 8-hour TTL and in-memory storage.

```java
AdminAuth admin = new AdminAuth(dbUser, dbPassword);
if (admin.isLoggedIn(httpExchange)) {
    // Show admin panel
}
```

#### 🌐 **HttpApiServer**
Embedded JDK HTTP Server with RESTful endpoints and HTML admin dashboard.

#### 🟢 **PresenceRepository**
Tracks online/offline player status with two modes:

- **Snapshot Mode:** All listed players online, others offline
- **Event Mode:** Update only specified players

```java
// Snapshot mode - mark all others offline
{
  "snapshot": true,
  "players": [{xuid, name, ip, hwid}, ...]
}

// Event mode - selective updates
{
  "players": [{xuid, online: true/false}, ...]
}
```

---

## 🔌 API Endpoints

### 🟢 **Server API** (Game Server Integration)

#### Health Check
```http
GET /api/server/health
```

**Response:**
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

**Request:**
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

#### Ban Changes Sync
```http
GET /api/server/bans/changes?since=2026-02-24T12:00:00Z
X-Server-Key: server_1
X-Server-Token: secret_token_here
```

**Response:**
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

#### Presence Update (Player Online/Offline)
```http
POST /api/server/presence/batch
Content-Type: application/json
X-Server-Key: server_1
X-Server-Token: secret_token_here
```

**Snapshot Mode (Recommended):**
```json
{
  "snapshot": true,
  "players": [
    {
      "xuid": "2533274790299905",
      "name": "PlayerName",
      "ip": "192.168.1.100",
      "hwid": "device_id"
    }
  ]
}
```

**Response:** Empty (200 OK)

### 🟡 **Admin UI** (Web Dashboard)

```http
GET /admin/login                # Login page
GET /admin/players              # All players
GET /admin/bans                 # Ban list
GET /admin/player?xuid=...      # Player details
POST /admin/player/ban          # Ban a player
POST /admin/player/unban        # Revoke ban
GET /admin/logout               # Logout
```

---

## 📊 Data Model

### `players` Table
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

### `player_stats` Table
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

### `bans` Table
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

## 🎮 Client Integration Example

### Ban Sync Implementation

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
                unbanPlayer(xuid);
            } else {
                banPlayer(xuid, reason, expiresAt);
            }
            
            lastSince = change.get("updatedAt").asText();
        }
        
        saveLastSince(lastSince);
    }
}
```

**Best Practices:**
- ✅ Idempotent: Multiple ban/unban calls are safe
- ✅ Offline-Tolerant: Players can be offline
- ✅ Persistent: Store `lastSince` between restarts
- ✅ Polling-Loop: Handle paginated results

---

## 🔐 Security

### Server-to-Backend Auth

```yaml
serverAuth:
  enabled: true
  token: "your-secure-token-min-32-chars"
```

**Required Headers:**
```
X-Server-Key: server_1
X-Server-Token: <token_from_database>
```

### Admin Dashboard Auth

- **Cookie-based session:** `BB_ADMIN_SESSION`
- **TTL:** 8 hours
- **Credentials:** DB username/password
- **Hash:** PBKDF2 (120,000 iterations)

### Password Hashing

Generate a hash:
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.PrintPasswordHash" \
  -Dexec.args="mySecurePassword123"
```

Output:
```
pbkdf2$120000$<salt>$<hash>
```

Configure in `backend.yml`:
```yaml
admin:
  rootPasswordHash: "pbkdf2$120000$<salt>$<hash>"
```

---

## ⚙️ Configuration Reference

### `backend.yml` - Complete

```yaml
web:
  bind: "0.0.0.0"          # Bind address
  port: 8080               # HTTP port

db:
  jdbcUrl: "jdbc:mysql://..."
  username: "banbridge_user"
  password: "strong_password"
  # HikariCP Pool Config
  # - Max: 10 connections
  # - Connection Timeout: 30s
  # - Idle Timeout: 10m
  # - Max Lifetime: 30m

serverAuth:
  enabled: true
  token: "token_from_database"

admin:
  serverName: "MyGameServer"
  rootPasswordHash: ""

limits:
  banChangesMaxRows: 1000
```

---

## 📈 Performance

### Connection Pooling (HikariCP)
- **Max Connections:** 10
- **Connection Timeout:** 30s
- **Idle Timeout:** 10 minutes
- **Max Lifetime:** 30 minutes

### Optimized Queries
- **Indexed:** `bans.updated_at`, `bans.player_id`
- **Bulk Operations:** `INSERT ... ON DUPLICATE KEY UPDATE`
- **Transaction Isolation:** READ_COMMITTED

### Benchmarks
- `GET /api/server/bans/changes`: ~50ms (1000 changes)
- `POST /api/server/stats/batch`: ~100ms (100 players)
- `GET /admin/players`: ~200ms (10,000 players)

---

## 🔄 Event System

### LiveBus - Server-Sent Events

```java
// Publish event
LiveBus.publishInvalidate("players");

// Subscribe (SSE)
GET /events/stream?channel=players
```

**Channels:**
- `players` - Player list updated
- `bans` - Ban status changed
- `stats` - Player stats updated
- `presence` - Online/offline status

---

## 🧪 Testing & Development

### Run Tests
```bash
mvn test
```

### Start Server
```bash
mvn exec:java -Dexec.mainClass="org.backendbridge.BackendMain"
```

### Test with curl

**Health:**
```bash
curl http://localhost:8080/api/server/health
```

**Stats:**
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

**Presence:**
```bash
curl -X POST http://localhost:8080/api/server/presence/batch \
  -H "Content-Type: application/json" \
  -H "X-Server-Key: server_1" \
  -H "X-Server-Token: secret" \
  -d '{
    "snapshot": true,
    "players": [{
      "xuid": "2533274790299905",
      "name": "TestPlayer",
      "ip": "192.168.1.100",
      "hwid": "device_123"
    }]
  }'
```

---

## 🐛 Troubleshooting

### MySQL Connection Error
```
Error: Public Key Retrieval is not allowed
```
**Solution:** `backend.yml` includes `allowPublicKeyRetrieval=true`

### Admin Login Failed
```
Error: Bad credentials
```
**Check:** Username = DB user, Password = DB password

### Too Many Changes
```
HTTP 413 Payload Too Large
```
**Solution:** Increase `limits.banChangesMaxRows` or implement pagination

### Database Lock
```
MySQL Error 1205: Lock wait timeout exceeded
```
**Fix:**
1. Increase connection pool size
2. Prevent deadlocks with proper lock ordering
3. Increase MySQL `max_connections`

---

## 📦 Dependencies

| Library | Version | Purpose |
|---------|---------|---------|
| Jackson Databind | 2.17.2 | JSON processing |
| SnakeYAML | 2.2 | YAML configuration |
| MySQL Connector/J | 9.3.0 | Database driver |
| HikariCP | 5.1.0 | Connection pooling |
| SLF4J | 2.0.16 | Logging |

---

## 🚀 Production Deployment

### Docker

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

### Kubernetes

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

**Health Endpoint:**
```bash
curl http://backend:8080/api/server/health
```

**Logs:**
```bash
tail -f /var/log/banbridge/app.log
```

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open pull request

### Code Style
- Java 17+ (records, text blocks)
- Jackson annotations for JSON
- Try-with-resources for resources
- SLF4J for logging

---

## 📄 License

Proprietary - All rights reserved.

---

## 🆘 Support

**Issues:** GitHub issues  
**Documentation:** See `/anleitung.txt`  
**Team:** BanBridge Development Team

---

<div align="center">

### ⭐ If this helps you, give it a star! ⭐

**Made with ❤️ by the BanBridge Team**

[🌐 Website](#) • [📖 Docs](#) • [💬 Discord](#) • [🐛 Issues](#)

</div>


## Credits
Built for admins who are tired of “it says online, but he’s gone” — and want a system that stays correct under real-world conditions.
```
