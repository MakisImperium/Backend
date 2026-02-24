``` markdown
# BackendBridge + BanBridge (Nukkit)
**Bans, Presence & Metrics — reliable, live, and drama‑free.**

BackendBridge is a lightweight **Java 17** backend (HTTP API + SSR Admin UI) for game servers. BanBridge is the matching **Nukkit plugin** that syncs bans, reports player presence, and pushes server metrics/stats.

> The goal is simple: stop guessing whether someone is “still online”. Make it correct by design.

---

## Highlights

### Admin Web UI (SSR)
- Login + roles/permissions
- Players list + player details
- Bans list + ban/unban
- Audit log (admin actions)
- Live stats dashboard (SSE + history)

### Server → Backend Sync (Plugin)
- **Presence Snapshot Mode** (full online list, even if empty)
- Ban changes pull + auto‑kick newly banned players
- Stats batches (playtime/kills/deaths)
- Metrics push (RAM/CPU/TPS/network)

### Built for stability
- Client retry/backoff
- Backend presence logic that prevents “stuck online” states
- Optional backend “stale presence” sweeper as a safety net

---

## How it works (high level)
```

[Nukkit Server + BanBridge Plugin] | | HTTP (token auth) v [BackendBridge Service] -----> [MySQL/MariaDB] | v [Admin Web UI]``` 

### Presence is snapshot‑based (recommended)
The plugin periodically sends:
```

json { "snapshot": true, "players": }``` 

Backend behavior:
- Every player in the snapshot → `online = 1`
- Every previously online player *not in the snapshot* → `online = 0`

This is what makes presence **reliable**, even if you don’t get clean quit/disconnect events.

---

## Requirements

### Backend
- **Java 17**
- Maven
- MySQL or MariaDB (UTF8MB4 recommended)

### Plugin (Nukkit)
- Nukkit server
- Network access to BackendBridge

---

## Quick start (BackendBridge)

### 1) Create the database schema
Run `schema.sql` on your MySQL/MariaDB server.

> Warning: the schema file may drop/create the database (depending on how you run it). Do not run it on a production DB without backups.

### 2) Configure the backend
Edit `src/main/resources/backend.yml` (or the runtime config you use).

You need:
- web bind + port
- DB credentials
- server auth enabled and tokens/keys configured (depends on your `AuthService` implementation)

Example (placeholders):
```

yaml web: bind: "0.0.0.0" port: 8080
db: host: "<DB_HOST>" port: 3306 database: "banbridge" username: "<DB_USER>" password: "<DB_PASSWORD>"
auth: enabled: true``` 

### 3) Build & run
Example:
```

bash mvn -q -DskipTests package java -jar target/<BACKEND_JAR_NAME>.jar``` 

Open:
- Health: `GET /api/server/health`
- Admin UI: `GET /admin/login`

---

## Admin UI setup (users & passwords)

This project includes utilities/classes to manage password hashing and users.

Recommended approach:
- Generate a password hash using the provided helper (e.g. `PrintPasswordHash`)
- Insert/update the admin user in `web_users` accordingly
- Do **not** commit real passwords or secrets into GitHub

> Always use placeholders in any public docs/configs: `<DB_PASSWORD>`, `<SERVER_TOKEN>`, etc.

---

## Quick start (BanBridge Nukkit plugin)

### 1) Install
- Put the plugin `.jar` into your Nukkit `plugins/` folder
- Start the server once to generate the default config

### 2) Configure
Set at minimum:
- `api.baseUrl` → where BackendBridge is reachable
- `api.serverKey` → **unique** per server instance
- `api.serverToken` → must match backend configuration

Example:
```

yaml api: baseUrl: "http://<BACKEND_HOST>:8080" serverKey: "survival-1" serverToken: "<SERVER_TOKEN>"
sync: presenceSeconds: 15 bansPollSeconds: 10 metricsSeconds: 15 statsFlushSeconds: 60 commandsPollSeconds: 3``` 

### 3) Presence Snapshot Mode (important)
The plugin sends snapshots **even if 0 players are online**.

For this to actually fix “stuck online” players, the backend must:
- read the full request body (including `snapshot: true`), not just the `players` array
- apply snapshot semantics (missing players → offline)

If this is implemented correctly, the “still online” issue disappears.

---

## Presence best practices (recommended values)

- `sync.presenceSeconds`: **15**
- backend “stale presence timeout”: **~90s** (good default)
- backend sweep interval: **30s** (lightweight and effective)

### Why a timeout sweeper at all?
If the game server crashes, no more snapshots arrive. Without a timeout, the last known presence can remain “online” forever.

The timeout is the safety belt. Snapshot mode is the airbag.

---

## Reverse proxy & HTTPS (production)

If you expose the backend publicly:
- Put it behind a reverse proxy (Nginx/Caddy/Traefik)
- Use HTTPS
- Consider IP restrictions for `/admin/*`
- Add rate limits if needed

---

## Troubleshooting

### Player is “online” but is actually offline
Checklist:
1. Plugin sends presence snapshots regularly (check plugin logs)
2. Backend endpoint `/api/server/presence` receives **`snapshot=true`** and uses it
3. DB sanity check:
   - `online = 1` but `online_updated_at` is very old → sweeper/timeout missing or not running
4. Make sure you **do not reuse the same `serverKey`** for multiple server instances

### Backend unreachable
- `api.baseUrl` must not point to `localhost` unless backend runs on the same machine
- Check firewall and port mapping
- Test `GET /api/server/health`

### Commands poll / ACK issues
- Ensure `serverKey` matches exactly
- Network instability can cause retries; that’s expected

---

## Security notes
- Never commit tokens/passwords into Git
- Treat DB credentials and server tokens as secrets
- Use strong admin passwords
- Prefer HTTPS in production

---

## Roadmap ideas
- Presence isolation per server instance (server_key scoping for online/offline)
- UI: server filter / “presence by server”
- Optional webhooks (Discord) for ban events

---

## License
Pick a license (MIT / Apache-2.0 / GPL, etc.) and add a `LICENSE` file.

---

## Credits
Built for admins who are tired of “it says online, but he’s gone” — and want a system that stays correct under real-world conditions.
```
