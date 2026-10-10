# Gatekeeper context

Plug-and-play API gateway. A backend owner registers in the management service, points a public path at their own backend, and sends traffic to the smart gateway. The gateway does not contain backend business logic.

This file is the running project context. Update it when behavior, ports, or contracts change.

## Services

| Service | Port | Owns | Does not own |
| --- | --- | --- | --- |
| `smart-gateway-service` | 8080 | Live request path: match, auth, rate limit, idempotency, proxy, publish access event | Route configuration storage |
| `api-management-service` | 8081 | Clients, routes, policies in MySQL. Admin audit. Config-change signal | Request proxying |
| `analytics-service` | 8082 | Access events in MongoDB and a short summary | Routing decisions |

The React control plane lives in `frontend` and runs at http://localhost:5174. It calls the management API on port 8081. Port 8081 itself is not the UI.

## How a request moves

1. Client calls the gateway (`http://localhost:8080/...`), not the backend.
2. Correlation filter sets `X-Correlation-Id` (reuses the incoming header when present).
3. Gateway matches the longest public path in its in-memory snapshot.
4. If the route requires auth, `X-Api-Key` decides who may call it. The owning client's key always passes. Another client's key passes only if that client belongs to the same admin and has one of the route's `allowedRoles`. Keys from other admins are treated as unknown (401).
5. Redis fixed-window rate limit, unless `rateLimit` is `0` (unlimited).
6. For `idempotentPost` routes, POST must send `Idempotency-Key`. A completed response is replayed from Redis for 24 hours. A key that is still running returns 409.
7. Gateway strips the public prefix when `stripPrefix` is true, adds the route's request headers, and forwards with the route timeout.
8. After the response, the gateway publishes an access event to Kafka. A Kafka failure does not fail the client request.
9. Analytics consumes that event and stores it in MongoDB.

`/health` on each service is outside the proxy and does not require a key.

## How config stays loosely coupled

- MySQL is the source of truth for routes and policies.
- The gateway keeps a local snapshot only. It does not open MySQL.
- On startup, and every 30 seconds, it pulls `GET /internal/snapshot` from management.
- After a client or route change is committed, management publishes `gateway.config.changed`. That message is only a hint. The gateway reloads the full snapshot. A missed message is healed by the next poll.
- Each gateway instance uses its own Kafka group id, so every instance receives the hint. Analytics uses one shared group so each access event is stored once.
- Services share JSON field names, not a Java library.

## Infra

`docker-compose.yml` at the repo root:

| Container | Host port | Dev credentials |
| --- | --- | --- |
| MySQL 8.4 | 3307 on the host, 3306 inside the container | database `gatekeeper`, user `root`, password `root`. Host port is 3307 because a local MySQL already uses 3306. |
| Redis 7.4 | 6379 | no password |
| Kafka 3.9 (KRaft, no Zookeeper) | 9092 | plaintext |
| MongoDB 7 | 27017 | no auth, database `gatekeeper` |

Dev admin header for management, the internal snapshot, and analytics reads: `X-Admin-Key: dev-admin-key`.

API keys are stored in plain text so a local user can read them back. Do not use these defaults outside a local machine.

## Layout

```text
gatekeeper/
  backend/              smart gateway, API management, analytics
  frontend/             React control plane, port 5174
  deploy/kafka/         single-node Kafka image for Railway
  docker-compose.yml    MySQL, Redis, Kafka, MongoDB
  CONTEXT.md
  RAILWAY.md            Railway deployment guide
```

The caller's backend (the target) always stays outside this repo. To try the gateway without one, point a route at a public echo service: public path `/bin`, target `https://httpbin.org`, strip prefix on, then `http://localhost:8080/bin/get` returns httpbin's `/get`.

## Run

```bash
docker compose up -d
```

```bash
cd backend/api-management-service && ./mvnw spring-boot:run
cd backend/smart-gateway-service && ./mvnw spring-boot:run
cd backend/analytics-service && ./mvnw spring-boot:run
cd frontend && npm install && npm run dev
```

Open http://localhost:5174, register or log in, create a client, save a route whose target URL is their backend, then send a call. The gateway on port 8080 applies the policy and either forwards or returns 429. The dashboard shows the analytics events.

## Deploy (Railway Hobby)

[RAILWAY.md](RAILWAY.md) is the step-by-step guide. The deployment-relevant facts:

- Eight Railway services: management, gateway, analytics, frontend, Kafka (`deploy/kafka`), plus Railway's MySQL, Redis, and MongoDB templates.
- Each Java image sets `SPRING_PROFILES_ACTIVE=railway`. `application-railway.properties` has no defaults, so a missing variable fails startup instead of falling back to localhost.
- Java images cap the heap with `JAVA_TOOL_OPTIONS` (`-Xmx320m`, serial GC) to keep Hobby usage low. Kafka runs with a 256 MB heap.
- `railway.toml` files do not set `startCommand`, so the Dockerfile `CMD` and the nginx entrypoint (which renders `$PORT` into the config) run unchanged.
- The frontend bakes `VITE_API_BASE` in at build time. The Docker build fails if it is empty.
- MongoDB uses `spring.mongodb.uri` (Spring Boot 4 name). The old `spring.data.mongodb.uri` is ignored by Boot 4.
- Schema is created by Hibernate `ddl-auto=update`. There are no migration scripts.

## Interview flow

Two sides sit outside Gatekeeper. The **client** is the caller's app. The **target** is their backend. Gatekeeper never contains the target's business logic.

1. An admin opens the React frontend and registers the client plus the target routes: public path, target URL, methods, rate limit, auth, and whether POST is idempotent. That request goes to API management, which stores it in MySQL. Admin writes are audited.
2. When the smart gateway starts, it calls management `GET /internal/snapshot` and keeps that configuration in memory. The live request never reads MySQL.
3. The client calls the smart gateway, not the target. The gateway adds or reuses `X-Correlation-Id`.
4. The gateway matches the longest public path. If the route requires auth, it checks the client credential. Over the Redis rate limit, it returns 429 to the client and does not call the target. An idempotent POST is answered from Redis when the same `Idempotency-Key` was already completed. Both checks are Redis so this hop stays small.
5. If the checks pass, the gateway forwards to the target, with the route timeout and optional prefix strip. The target's response goes back to the client.
6. After the response, the gateway publishes one access event to Kafka: path, status, latency, correlation id, and whether it was a rate-limit or auth failure. Kafka being down does not fail the client.
7. Analytics consumes that topic and stores the event in MongoDB, so each client can be queried differently without a fixed SQL schema.
8. If the admin later changes a route in the React app, management commits MySQL and publishes `gateway.config.changed`. The smart gateway consumes that event and reloads the snapshot into memory. A missed message is healed by the 30 second poll.

The person who configures the gateway can register with email/password or use Google OIDC. Google uses the server-side authorization-code flow: management verifies Google's ID token, creates or links an admin by verified email, and redirects the browser with a two-minute, single-use code. The frontend exchanges that code for the same Gatekeeper JWT used by email/password login, then removes the code from the URL. Google secrets never reach the browser. Each account only sees the clients it created. Callers of the gateway still send `X-Api-Key`. The gateway's own pull of `/internal/snapshot` still uses the service key `X-Admin-Key`, which is not a user password. A route can separately require an end-user JWT in `Authorization: Bearer`, signed with the Gatekeeper HMAC secret. Admin Google OIDC and route user-JWT validation are separate features.

Register a backend, then call the gateway:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' | sed -E 's/.*"token":"([^"]+)".*/\1/')

curl -s -X POST http://localhost:8081/api/clients \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"shop","apiKey":"gk_shop","roles":["orders"]}'

# use the returned client id
curl -s -X POST http://localhost:8081/api/clients/CLIENT_ID/routes \
  -H 'Content-Type: application/json' -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "shop",
    "publicPath": "/shop",
    "targetUrl": "https://httpbin.org",
    "methods": ["GET", "POST"],
    "authRequired": true,
    "allowedRoles": ["orders"],
    "rateLimit": 30,
    "rateWindowSeconds": 60,
    "idempotentPost": true,
    "stripPrefix": true,
    "requestHeaders": {"X-Source": "gatekeeper"}
  }'

curl -s http://localhost:8080/shop/get -H 'X-Api-Key: gk_shop'
curl -s http://localhost:8080/health/routes
curl -s http://localhost:8082/api/summary -H 'X-Admin-Key: dev-admin-key'
```

`GET /shop/get` is forwarded to `https://httpbin.org/get`. Wait a couple of seconds for the Kafka consumer before reading analytics. `GET /health/routes` shows how many routes are in memory. If it is `0`, management was down at startup; it retries every 30 seconds, or a config change pushes a refresh.

## Management API

`/api/auth/register`, `/api/auth/login`, `/api/auth/options`, and the single-use `/api/auth/oidc/exchange` are open. Google login starts at `/oauth2/authorization/google` and returns through `/login/oauth2/code/google`. Other `/api/**` routes need `Authorization: Bearer <token>` and only see the caller's own clients. `/internal/**` needs the service key `X-Admin-Key`.

- `POST /api/clients` body: `name`, optional `apiKey`, optional `roles`
- `GET /api/clients` and `GET /api/clients/{id}`
- `DELETE /api/clients/{id}`
- `POST /api/clients/{clientId}/routes`
- `GET /api/clients/{clientId}/routes`
- `PUT /api/clients/{clientId}/routes/{routeId}`
- `DELETE /api/clients/{clientId}/routes/{routeId}`
- `GET /internal/snapshot` for the gateway
- `GET /api/audit` last 50 admin writes
- `GET /api/console/overview?clientId=` dashboard data, proxied from analytics after an ownership check

Route defaults: timeout 5000 ms, auth required, 60 requests per 60 seconds, prefix stripped, idempotent POST off, route enabled. `rateLimit: 0` disables the limit. `publicPath` is unique across all clients. `targetUrl` must be `http` or `https`.

Write calls are audited with an AspectJ `@Audited` advice after the controller method returns. The audit row is stored in MySQL table `admin_audit`. A failed audit does not undo the change.

Request pipeline in management: servlet filters (`AdminKeyFilter` for auth, `CorrelationFilter` for `X-Correlation-Id` and the log MDC), then the Spring MVC `RequestTimingInterceptor`, which logs method, path, status, duration, and handler for `/api/**` and `/internal/**`. `ApiExceptionHandler` (`@RestControllerAdvice`) turns every error into `{"error","message"}` JSON: 400 for bad input, 409 for duplicates, Spring's own status for framework errors, 500 for anything unexpected (logged with the correlation id). Analytics has the same kind of handler. The gateway has no controllers on the proxy path, so `GatewayProxyFilter` catches errors itself and returns JSON with `correlationId`.

Correlation id path: browser or caller → management (console Send forwards it) → gateway → target backend, then gateway → Kafka event → analytics (the listener puts it in the log MDC) → MongoDB `correlationId`.

## Gateway behavior worth remembering

- No route: 404. Path matches but method does not: 405.
- Missing, unknown, or other-admin API key: 401. Same-admin client without an allowed role: 403.
- Example: admin owns client `shop` (routes `/shop/**`, allowed roles `orders`), client `mobile-app` (role `orders`), and client `partner` (role `news`). `shop` and `mobile-app` keys can call `/shop/order`; `partner` gets 403.
- Redis down while a limit or idempotency check is required: 503.
- Backend timeout: 504. Backend unreachable: 502.
- Request and response bodies are buffered so idempotency can store them. Stored POST bodies are capped at 256 KB; larger ones are not replayed.
- `X-Api-Key` is not forwarded to the backend. `X-Correlation-Id` is forwarded.
- Hop-by-hop headers are dropped.

## Analytics

- Topic `gateway.access.events`
- Mongo collection `access_events`
- `GET /api/events?clientId=&limit=`
- `GET /api/summary?clientId=` summarizes the latest 200 stored events, not the full history
- `GET /api/overview?clientId=` builds the admin dashboard from the latest 500 events: volume over time, status mix, busiest paths, average and p95 latency, rate-limit count

The React console shows that overview for the selected client. Callers of the gateway do not read analytics. The rate limit bucket is per route and per API key, so two keys on the same route do not share a counter. A route can require a user JWT (`Authorization: Bearer`, HS256, `gatekeeper.jwt.secret`) in addition to the client API key. Request bodies larger than 1 MB are rejected with 413. Local connection settings and secrets can be overridden with environment variables, including `MYSQLHOST`/`MYSQLPASSWORD`, `REDIS_URL`, `MONGODB_URI`, `KAFKA_BOOTSTRAP_SERVERS`, `GATEKEEPER_ADMIN_KEY`, and `GATEKEEPER_JWT_SECRET`. Railway images use the `railway` Spring profile and require their deployment settings instead of falling back to local credentials; see [RAILWAY.md](RAILWAY.md). HTTPS belongs on the reverse proxy in front of the gateway.

Event JSON fields: `eventId`, `correlationId`, `clientId`, `routeId`, `method`, `path`, `status`, `latencyMs`, `timestamp`, `error`.

## What is intentionally simple

- No shared Maven module. Contracts are duplicated records and the JSON fields above.
- Admin login supports password credentials and optional Google OIDC. Route auth remains client API key plus roles, with an optional HS256 user JWT; Google admin login does not automatically make proxied routes Google-protected.
- OIDC one-time codes are held in management-process memory for two minutes. A multi-replica management deployment should move them to Redis.
- Access events are attributed to the route owner's client, so the owner's dashboard shows calls made by other clients' keys too.
- API keys are stored in plain text, the gateway cache is per process, and the proxy buffers the body.
- Unit tests cover path matching, target URLs, header encoding, and the flat JSON events. They do not boot MySQL, Redis, Kafka, or Mongo.

## Done in this pass

- Docker Compose for MySQL, Redis, Kafka, and MongoDB.
- Management catalog, snapshot, Kafka hint after commit, AOP admin audit, validation, and error JSON.
- Gateway in-memory snapshot, proxy filter, Redis rate limit, Redis idempotency, correlation id, async access events.
- Analytics consumer, recent events, and a short summary.
