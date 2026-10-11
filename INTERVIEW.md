# Gatekeeper — interview notes (plain language)

Use this as a talking script. If you can explain the boxed “one sentence” under each section, you are ready.

---

## What it is (15 seconds)

Gatekeeper is a **front door for APIs**. A SaaS company’s website does not call their private pay or order service. It calls Gatekeeper. Gatekeeper checks “is this browser allowed?” and “is this a real logged-in user?”, then forwards to the private service.

We sell **control of that front door**, not the company’s business logic.

```text
Customer browser  →  Gatekeeper gateway (8080)  →  private order/pay/report
                         ↑
              tenant console (5174) talks to management (8081)
              which stores routes in MySQL and hints the gateway via Kafka
```

Two people use our product. They are **not** the same login.

| Who | URL | Job |
| --- | --- | --- |
| **Platform admin** (us) | `/admin` | Create a company after review. Suspend or remove it. Read contact requests. |
| **SaaS administrator** (the customer) | `/login` | Publish routes for *their* company only. |

End customers of Foodfast never log into Gatekeeper. They log into Foodfast. Foodfast’s UI then sends their user token to our gateway.

For a resume live demo, `/` has **Try demo**. That signs the visitor in as a seeded tenant (`Demo Kitchen`, slug `demo`) without opening public signup. Recruiters share that org. Real companies still request access.

---

## The story you tell first

1. A company fills the public contact page. We do **not** let them sign up themselves.
2. We create **Foodfast**, slug `foodfast`, and one tenant login (Priya).
3. Priya signs in at `/login` and maps `/order` → `https://orders.internal.foodfast.com`.
4. Callers use **`/foodfast/order`**. Quick Eats can also have `/order`; theirs is `/quick/order`. Slugs stop collisions.
5. Foodfast’s website calls `https://gateway.../foodfast/order` with `Origin: https://foodfast.com` and `Authorization: Bearer <user token from Google/Auth0>`.
6. Gateway checks origin + token, rate-limits in Redis, forwards, then writes a traffic event to Kafka. Analytics stores it in MongoDB. Priya sees it on the dashboard.

**Why not one login?** Platform staff provision tenants. Tenant admins must never see other companies. Separate pages (`/admin` vs `/login`) plus `portal` on the login API reject the wrong account type.

---

## Who puts which values where

Think in **three buckets**: values **we** type, values **Priya** types, values **Foodfast’s website** sends on each live call.

### A. Public page (`/`)

Anyone can read the product story and send a request (name, company, email, website, message). That lands in MySQL as an inquiry. Platform admin sees it. It does **not** create an account.

### B. Platform console — Add an organization

| Field | Why it exists | What we type | Where it comes from |
| --- | --- | --- | --- |
| Organization name | Human label | `Foodfast` | Sales / the inquiry |
| Slug | Prefix on every public path | `foodfast` | We choose a unique short name. Not `api` or reserved words. Soft-delete **retires** the slug so a new Foodfast can reuse `foodfast`. |
| Tenant admin name / email / temporary password | The one person who will publish routes | Priya’s work email | The inquiry. They sign in at `/login`. Google OIDC only works if this email was already provisioned. |

Activate / suspend / soft-delete: suspended or deleted orgs cannot log in; their routes **drop out of the gateway snapshot**, so live traffic stops.

### C. Organization console — Routes & policies (this is the form interviewers will ask about)

Priya is **not** inventing crypto. She copies values from her own product.

| Field | Why it is there | What the client puts | Where they get it |
| --- | --- | --- | --- |
| **Name** | Label in the list | `Orders API` | They make it up |
| **Public path** | The short path *they* think in (`/order`). We store `/foodfast/order` | `/order` | Their API design. Callers use `/foodfast/order` |
| **Target URL** | Private service Gatekeeper forwards to | `https://orders.internal.foodfast.com` | Their backend URL. Should **not** be public. On Railway this is often a private hostname |
| **Methods** | Which verbs this route accepts | `GET, POST` | Same as their real API. Wrong method → **405** |
| **Rate limit / minute** | Stop one caller flooding the backend | `60`. `0` = unlimited | They pick a number. Enforced in **Redis**, not in their code |
| **Timeout (ms)** | How long we wait for the backend | `5000` | They pick. Too slow → **504** |
| **Allowed browser origins** | Browser CORS. “Which website may call this route?” | `https://foodfast.com` and maybe `http://localhost:5173` | The **origin of their UI** (scheme + host only, no path). DevTools → Network → `Origin` header. If missing/wrong → **403** `cors_origin_denied` |
| **User token issuer** | Who minted the customer’s JWT | `https://accounts.google.com` or Auth0 issuer URL | Identity provider docs. Google: `https://accounts.google.com`. Auth0: `https://YOUR_TENANT.auth0.com/` |
| **Audience** | Which app the token was issued for | Google **OAuth client ID**, or Auth0 API identifier | Google Cloud → Credentials → OAuth client ID. Auth0 → APIs → Identifier. If issuer is set, audience is required |
| **Header rules** | Extra headers we add so the **private** service can trust us | `X-Source=gatekeeper` | They invent a shared secret header. Their backend rejects traffic that does not have it. Incoming `X-Api-Key` and spoofed `X-Gatekeeper-User` are **stripped** |
| **Strip prefix** | Backend might expect `/` not `/foodfast/order` | Usually **on** | If on, gateway calls target with the leftover path (often `/` or `/get`) |
| **Idempotent POST** | Same payment POST twice should not charge twice | On for pay | UI must send `Idempotency-Key`. Redis remembers the response **24 hours**. In-flight duplicate → **409** |
| **Require user token** | Force `Authorization: Bearer` | Usually **on** for browser APIs | If they also set issuer, we verify against that IdP’s **JWKS**. If no issuer, we verify an HS256 JWT with our `GATEKEEPER_JWT_SECRET` (legacy). Bad token → **401**. IdP unreachable → **503** |

We **removed required scopes** from the form. Scopes would extra-check `scope`/`scp` on the JWT. Google ID tokens are `openid email profile`; they do not give useful per-API permissions. Issuer + audience is enough.

**Request lab** is only for Priya to debug. She pastes a real user token and origin. That is not how production works; production is their website.

### D. What Foodfast’s **website** sends (live traffic)

This is the only request that hits the gateway in production:

```http
POST /foodfast/order HTTP/1.1
Host: <gateway>
Origin: https://foodfast.com
Authorization: Bearer <access token from their IdP>
Idempotency-Key: <uuid>          # only if that route has idempotent POST
Content-Type: application/json

{ ... order body ... }
```

Where the token comes from: the user already signed into Foodfast. The UI asks Google/Auth0 for a token whose **audience** is Foodfast’s app. Gatekeeper does **not** log the user in. It only **checks** the token (signature via JWKS, issuer, audience, expiry) and then sets `X-Gatekeeper-User` to the JWT `sub` for the backend.

---

## Internal working (the part they ask after “what is it”)

### Four services, four jobs

| Piece | Port | Remembers | Does |
| --- | --- | --- | --- |
| **Frontend** | 5174 | Nothing important | React consoles |
| **Management** | 8081 | **MySQL** = source of truth | Orgs, admins, routes, inquiries, audit. Issues console JWT. Snapshot API for the gateway |
| **Gateway** | 8080 | **RAM snapshot only** | Every live request. Never opens MySQL |
| **Analytics** | 8082 | **MongoDB** | Stores access events, builds the dashboard |

Plus infra: **MySQL, Redis, Kafka, MongoDB**.

**One-liner if they ask why four data stores:** MySQL is config. Redis is fast counters for *this second*. Kafka is “tell the others something happened” without coupling. Mongo is a dump of traffic events that we query by client without a rigid SQL schema.

### How a live request is processed (say this in order)

1. Correlation filter: keep or create `X-Correlation-Id` (same id in logs, Kafka, Mongo).
2. Match **longest public path** in the in-memory snapshot. No match → 404. Wrong method → 405.
3. CORS: if the route has allowed origins, `Origin` must be on the list. `OPTIONS` preflight → 204, skip auth and skip Kafka event.
4. User token (and historically API key). Browser product path: OIDC JWKS.
5. Redis rate limit. Over limit → **429**, do not call the backend.
6. Idempotent POST: Redis replay or lock.
7. Strip prefix, add header rules, set `X-Gatekeeper-User`, forward with timeout. Drop hop-by-hop headers and upstream CORS headers.
8. Return the backend response.
9. **Then** publish Kafka access event. Kafka down does **not** fail the user.

### How config reaches the gateway (loose coupling)

- Gateway on start, and **every 30 seconds**, `GET /internal/snapshot` with service key `X-Admin-Key`.
- When Priya saves a route, management writes MySQL, then publishes Kafka topic **`gateway.config.changed`**. Message is only a **hint** (“reload”). Gateway pulls the full snapshot again.
- If Kafka drops the hint, the 30-second poll heals it.
- Only **ACTIVE** orgs appear in the snapshot.

We **share JSON field names**, not a common Java library. That is a deliberate interview point: services stay independently deployable.

---

## Kafka — what we actually use

Two topics. Plain JSON strings. Spring Kafka.

| Topic | Who writes | Who reads | Why |
| --- | --- | --- | --- |
| `gateway.config.changed` | Management, after a catalog write | **Every gateway instance** | Wake up, reload snapshot |
| `gateway.access.events` | Gateway, after a proxied call (not on preflight) | **Analytics, once** | Traffic log |

**Consumer groups (this is the trick question):**

- Gateway group id is **`smart-gateway-<random uuid>` per process**. So **every** gateway replica gets the config hint (broadcast).
- Analytics group id is **`analytics-service`** (shared). So each access event is stored **once** even if you scale analytics.

Producer key for access events is the **correlation id**. Config-change key is the **client id**. Failures are logged; they do not roll back the HTTP response.

Local Kafka is KRaft (no Zookeeper), port **9092**.

---

## Redis — what we actually use

Only the **gateway** uses Redis. String values via `StringRedisTemplate`.

**1. Rate limit (fixed window)**

- Key: `rl:{routeId}:{identity}:{windowBucket}`
- `INCR`. First hit sets TTL = window (60 seconds).
- If count > limit → 429.
- Identity, in order: hashed API key `k:…`, else hashed JWT `sub` `u:…`, else hashed IP `ip:…`. Two users do not share one bucket.
- Redis down while a limit is required → **503**.

**2. Idempotent POST**

- Key: `idem:{routeId}:{Idempotency-Key}`
- `SETNX` to `INFLIGHT` for 60s (start work).
- Already `INFLIGHT` → 409.
- Success: store status + content type + body (Base64), TTL **24 hours**. Next POST with the same key replays.
- 5xx or body > 256 KB: delete the key (do not cache a failure / huge body).

Redis is **not** our config store and **not** our analytics store.

---

## MySQL and Mongo, quickly

**MySQL (management):** organizations, admins (password hash + role), clients, routes, inquiries, `admin_audit`. Hibernate `ddl-auto=update`. Unique slug. Tenant routes are prefixed. Writes go through `@Audited` AOP (audit failure does not undo the change).

**Mongo (analytics):** collection `access_events`. Fields: `eventId`, `correlationId`, `clientId`, `routeId`, `method`, `path`, `status`, `latencyMs`, `timestamp`, `error`. Dashboard uses the latest few hundred events, not forever-history SQL.

---

## Auth in two layers (easy to mix up — don’t)

| Layer | Who | What |
| --- | --- | --- |
| **Console login** | Priya / platform staff | Password or Google OIDC against **management**. Result is a Gatekeeper JWT in `localStorage`. Protects `/api/clients`, platform APIs. **Does not** protect `/foodfast/order`. |
| **Route user token** | Foodfast’s end user | Bearer token checked by the **gateway** using the route’s issuer + audience (JWKS). |

Service-to-service inside Gatekeeper: `X-Admin-Key` for snapshot and analytics. That is a **machine** key, not a user role.

Login API may send `portal: "TENANT"` or `"PLATFORM"`. Wrong type → 403 `wrong_portal`.

---

## Status codes worth reciting

| Code | Meaning |
| --- | --- |
| 204 | CORS preflight OK |
| 401 | Bad/missing user token or API key |
| 403 | Bad Origin, or wrong portal, or signup closed |
| 404 | No route |
| 405 | Path exists, method does not |
| 409 | Idempotency key still in flight, or duplicate slug/path |
| 413 | Body > 1 MB |
| 429 | Rate limit |
| 502 | Backend unreachable |
| 503 | Redis down, or IdP JWKS unreachable |
| 504 | Backend timeout |

---

## Honest answers (they like this)

- **Why not API Gateway from AWS?** We own the product: per-tenant slug, provisioned orgs, origin + OIDC in one console, our analytics. It is also a learning/system-design piece: split control plane vs data plane.
- **Why Kafka if we also poll?** Hint for low latency; poll for safety. At-least-once config reload is fine (reload is idempotent).
- **Why Mongo for events?** High write, flexible documents, dashboard by `clientId`. Config stays relational.
- **What is still simple?** API keys stored in plain text locally; OIDC one-time codes in management memory (2 minutes — multi-replica should use Redis); gateway snapshot is per process; proxy buffers the body; no shared Maven module.
- **Production topology:** UI → public gateway → **private** backends. Put HTTPS on the reverse proxy in front. Railway: eight services, no secret defaults in the `railway` profile.

---

## 60-second close

“Gatekeeper is a multi-tenant API gateway. Control plane is management + MySQL. Data plane is the gateway with an in-memory snapshot. Redis does rate limits and idempotency. Kafka has two topics: config-changed as a reload hint, access events for analytics in Mongo. Tenants publish `/order`; the world calls `/foodfast/order` with their website origin and their IdP token. We never host their business logic.”

Local demo: docker compose, three Spring Boot apps, UI at http://localhost:5174 — `/` contact, `/login` tenant, `/admin` platform. Gateway http://localhost:8080.
