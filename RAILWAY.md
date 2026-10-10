# Deploying Gatekeeper on Railway (Hobby plan)

One Railway project, eight services, all from this GitHub repo or Railway templates. Service-to-service traffic uses Railway private networking; only the frontend, management, and gateway get public domains.

| Railway service name | Source | Root Directory | Config File | Public domain |
| --- | --- | --- | --- | --- |
| `MySQL` | Railway MySQL template | | | No |
| `Redis` | Railway Redis template | | | No |
| `MongoDB` | Railway MongoDB template | | | No |
| `kafka` | This repo | `/deploy/kafka` | `/deploy/kafka/railway.toml` | No |
| `management` | This repo | `/backend/api-management-service` | `/backend/api-management-service/railway.toml` | Yes, port 8081 |
| `analytics` | This repo | `/backend/analytics-service` | `/backend/analytics-service/railway.toml` | No |
| `gateway` | This repo | `/backend/smart-gateway-service` | `/backend/smart-gateway-service/railway.toml` | Yes, port 8080 |
| `frontend` | This repo | `/frontend` | `/frontend/railway.toml` | Yes |

Use these service names exactly. The variable blocks below reference them (`${{management.RAILWAY_PRIVATE_DOMAIN}}` and so on). If you rename a service, change the references too.

In a monorepo, Railway does not find `railway.toml` from the Root Directory on its own. Set **Settings → Config-as-code → Railway Config File** to the path in the table for every repo-backed service.

Targets you register on the deployed gateway must be reachable from Railway, so use a public `https://` URL, not `localhost`.

## 1. Push the code

Railway builds from GitHub. Commit and push this repo first. Never commit real secrets; all secrets go into Railway variables.

## 2. Create the project and data services

1. Railway → **New Project → Empty project**.
2. **Create → Database → MySQL**, then **Redis**, then **MongoDB**. Keep their default names `MySQL`, `Redis`, and `MongoDB`.
3. **Create → GitHub Repo → this repo**. Rename the service `kafka`, set Root Directory `/deploy/kafka` and Config File `/deploy/kafka/railway.toml`. Then **right-click the service → Attach volume**, mount path `/var/lib/kafka/data`. Do not add a public domain.

The Kafka image is a single KRaft broker with a 256 MB heap. It advertises `${RAILWAY_PRIVATE_DOMAIN}:9092` on its own, so it needs no variables.

## 3. Shared secrets

**Project Settings → Shared Variables**, add:

| Name | Value |
| --- | --- |
| `GATEKEEPER_ADMIN_KEY` | output of `openssl rand -hex 32` |
| `GATEKEEPER_JWT_SECRET` | output of `openssl rand -hex 48` (must be at least 32 characters) |

`GATEKEEPER_ADMIN_KEY` is the service key between management, gateway, and analytics. `GATEKEEPER_JWT_SECRET` signs console logins and verifies the legacy HMAC user JWT when a route has no OIDC issuer. Changing the JWT secret logs everyone out.

## 4. Application services

Create each of the four app services from the repo (**Create → GitHub Repo**), rename it, set Root Directory and Config File from the table, then paste its variables in **Variables → Raw Editor**.

`PORT` is set explicitly so the private URLs below have a known port.

### management

```env
PORT=8081
MYSQLHOST=${{MySQL.MYSQLHOST}}
MYSQLPORT=${{MySQL.MYSQLPORT}}
MYSQLDATABASE=${{MySQL.MYSQLDATABASE}}
MYSQLUSER=${{MySQL.MYSQLUSER}}
MYSQLPASSWORD=${{MySQL.MYSQLPASSWORD}}
KAFKA_BOOTSTRAP_SERVERS=${{kafka.RAILWAY_PRIVATE_DOMAIN}}:9092
GATEKEEPER_ADMIN_KEY=${{shared.GATEKEEPER_ADMIN_KEY}}
GATEKEEPER_JWT_SECRET=${{shared.GATEKEEPER_JWT_SECRET}}
GATEKEEPER_GATEWAY_URL=https://${{gateway.RAILWAY_PUBLIC_DOMAIN}}
GATEKEEPER_ANALYTICS_URL=http://${{analytics.RAILWAY_PRIVATE_DOMAIN}}:8082
GATEKEEPER_FRONTEND_ORIGIN=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
GATEKEEPER_OIDC_ENABLED=true
GOOGLE_OIDC_CLIENT_ID=<client ID from Google Cloud>
GOOGLE_OIDC_CLIENT_SECRET=<rotated client secret from Google Cloud>
```

The Google client secret is a management-service secret. Do not put it in Shared Variables, the frontend, or source control.

Before enabling OIDC, open **Google Cloud Console → APIs & Services → Credentials**, select the OAuth 2.0 Web application, and add this exact **Authorized redirect URI**:

```text
https://<management-public-domain>/login/oauth2/code/google
```

For this deployment, replace `<management-public-domain>` with the domain Railway generated for `management`. Configure the OAuth consent screen and add your Google account as a test user if the app is still in Testing status. A secret pasted into chat, logs, or source control must be revoked and replaced before deployment.

### analytics

```env
PORT=8082
MONGODB_URI=${{MongoDB.MONGO_URL}}/gatekeeper?authSource=admin
KAFKA_BOOTSTRAP_SERVERS=${{kafka.RAILWAY_PRIVATE_DOMAIN}}:9092
GATEKEEPER_ADMIN_KEY=${{shared.GATEKEEPER_ADMIN_KEY}}
```

Railway's `MONGO_URL` has no database name, so `/gatekeeper?authSource=admin` is appended.

### gateway

```env
PORT=8080
REDIS_URL=${{Redis.REDIS_URL}}
KAFKA_BOOTSTRAP_SERVERS=${{kafka.RAILWAY_PRIVATE_DOMAIN}}:9092
GATEKEEPER_MANAGEMENT_URL=http://${{management.RAILWAY_PRIVATE_DOMAIN}}:8081
GATEKEEPER_ADMIN_KEY=${{shared.GATEKEEPER_ADMIN_KEY}}
GATEKEEPER_JWT_SECRET=${{shared.GATEKEEPER_JWT_SECRET}}
```

### frontend

```env
VITE_API_BASE=https://${{management.RAILWAY_PUBLIC_DOMAIN}}
```

Vite bakes `VITE_API_BASE` into the JavaScript at build time. The Docker build fails if it is empty. If the management domain changes, redeploy the frontend.

## 5. Public domains

**Settings → Networking → Generate Domain** on:

- `management`, target port `8081`
- `gateway`, target port `8080`
- `frontend`, use the port Railway suggests (nginx listens on Railway's `PORT`)

Generate domains before the first successful frontend build, because `VITE_API_BASE` and `GATEKEEPER_FRONTEND_ORIGIN` reference them. If a build ran earlier with an empty domain, redeploy `frontend` and `management` once the domains exist.

## 6. Deploy order

Railway deploys everything at once. That is fine:

- Java services fail fast and are restarted (`ON_FAILURE`, 10 retries) if MySQL, Mongo, or Kafka are not ready yet.
- The gateway starts with zero routes if management is not up, then pulls the snapshot every 30 seconds.
- Health checks hit `/health` on each Java service and on the frontend, with a 300 second timeout.

Each `railway.toml` also sets `watchPatterns`, so a push only redeploys the services whose folder changed.

## 7. Smoke test

Replace the domains with your own.

```bash
curl https://<management-domain>/health
curl https://<gateway-domain>/health
curl https://<gateway-domain>/health/routes
```

Then open `https://<frontend-domain>`, register an account, create a client, and add a route. Production targets should be private Railway URLs or otherwise unreachable from the public internet. `https://httpbin.org` is only a smoke-test target. Example: public path `/bin`, target `https://httpbin.org`, strip prefix on, then:

```bash
curl https://<gateway-domain>/bin/get -H 'X-Api-Key: <client api key>'
```

The dashboard should show the call within a few seconds. If it does not, check `analytics` logs for Kafka or Mongo errors.

## Hobby plan cost

Hobby is $5/month and includes $5 of usage. Usage beyond that is billed for RAM, CPU, volumes, and egress. Eight always-on services will use more than the included $5. A rough idle footprint:

| Service | Approx. RAM |
| --- | --- |
| 3 Java services (heap capped at 320 MB each) | 1.2 to 1.5 GB |
| Kafka (256 MB heap) | ~400 MB |
| MySQL | ~350 MB |
| MongoDB | ~200 MB |
| Redis, nginx | < 50 MB |

That is about 2.5 GB, which at Railway's per-GB memory pricing is well above the $5 credit. Check the live estimate in **Project → Usage** after the first day and set a **usage limit** in workspace billing settings so a mistake cannot run up a bill.

Ways to cut cost without code changes:

- Use MongoDB Atlas free tier (M0) and put its URI in `MONGODB_URI`; delete the Railway `MongoDB` service.
- Use a free hosted Redis (for example Upstash) via `REDIS_URL`; Upstash requires `rediss://`.
- Remove the project between demos; MySQL data is lost unless you back it up.

The JVM heap can be changed per service by overriding `JAVA_TOOL_OPTIONS` in that service's variables. Do not go much below `-Xmx256m`.

## Production notes

- Hibernate `ddl-auto=update` creates and alters MySQL tables on startup. There are no migration scripts, so review entity changes before deploying.
- Kafka is a single broker. Losing its volume loses unconsumed access events only; route config is reloaded from MySQL.
- Analytics dashboards read the latest 500 events per client.
- Client API keys are stored in plain text in MySQL.
- TLS is terminated by Railway on the public domains. Private traffic between services is plain HTTP inside Railway's network.

Local development is unchanged: `docker compose up -d` plus `./mvnw spring-boot:run` uses the defaults in `application.properties`, and `frontend/.env` points at local management.
