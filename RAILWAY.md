# Deploying Gatekeeper on Railway

Gatekeeper runs as three Spring Boot services plus a React frontend. Each service has a `Dockerfile` and `railway.toml` in its directory. Create one Railway service per directory and set that directory as its **Root Directory**:

| Railway service | Root Directory | Public URL |
| --- | --- | --- |
| API management | `/backend/api-management-service` | Yes; browser console API |
| Smart gateway | `/backend/smart-gateway-service` | Yes; caller traffic |
| Analytics | `/backend/analytics-service` | No; management reads it |
| Frontend | `/frontend` | Yes; browser console |

Railway reads each directory's `railway.toml`. The Java containers activate the `railway` Spring profile, which requires production connection values and secrets rather than silently falling back to local development credentials. They listen on Railway's `PORT` (with their existing local ports as defaults); their `/health` endpoints are used for deployment health checks. The frontend container serves the Vite build through Nginx on `PORT` and checks `/health`.

## Provisioning and deployment order

The app requires MySQL, Redis, Kafka, and MongoDB. Provision compatible services on Railway or use externally hosted instances, then add the four application services. These dependencies must be available before their clients finish starting:

1. Provision MySQL, Redis, Kafka, and MongoDB, and copy their connection details into Railway variables.
2. Deploy **API management**, **analytics**, and **smart gateway**. Deploying management first gives the gateway a service to contact; the gateway retries its initial snapshot load if management is not ready yet.
3. Generate strong, unique values for `GATEKEEPER_ADMIN_KEY` and `GATEKEEPER_JWT_SECRET`. Use the same values for all three Java services. Set them as Railway secrets, never commit real values.
4. Enable public domains for API management and smart gateway. Set `GATEKEEPER_GATEWAY_URL` on API management to the gateway's public `https://` URL, and set `GATEKEEPER_MANAGEMENT_URL` on the gateway to API management's private Railway URL, for example `http://<management-private-domain>:<management-port>`. Set `GATEKEEPER_ANALYTICS_URL` on API management to analytics' private Railway URL.
5. Deploy the frontend. Set its Railway variable **`VITE_API_BASE` before building** to the public API management URL, such as `https://<management-domain>`. Vite embeds this value into the static bundle at build time, so changing only a runtime variable requires a rebuild/redeploy. Set `GATEKEEPER_FRONTEND_ORIGIN` on API management to the frontend's public origin, with no trailing slash (for example `https://<frontend-domain>`).

Use Railway private DNS for service-to-service traffic where indicated; do not use `localhost` between Railway services. `GATEKEEPER_GATEWAY_URL` intentionally uses the public gateway URL because management's console status endpoint probes that configured address. The frontend must use the public management URL because it runs in the visitor's browser.

## Variables

Set variables only on the services that use them. Railway service references can be used for the database/broker connection values if the provider exposes compatible values.

| Variable | Service(s) | Value |
| --- | --- | --- |
| `MYSQLHOST`, `MYSQLPORT`, `MYSQLDATABASE`, `MYSQLUSER`, `MYSQLPASSWORD` | API management | MySQL host, port, database, username, and password |
| `REDIS_URL` | Smart gateway | Complete Redis connection URL, including username/password when required |
| `MONGODB_URI` | Analytics | Complete MongoDB connection URI, including database and credentials |
| `KAFKA_BOOTSTRAP_SERVERS` | All Java services | Broker `host:port` list |
| `KAFKA_SECURITY_PROTOCOL` | All Java services | `PLAINTEXT`, `SSL`, `SASL_SSL`, or `SASL_PLAINTEXT`, as required by the broker |
| `KAFKA_SASL_MECHANISM`, `KAFKA_SASL_JAAS_CONFIG` | All Java services | Set when the broker requires SASL; put broker credentials in the JAAS value as Railway secrets |
| `GATEKEEPER_ADMIN_KEY` | All Java services | One shared, randomly generated service/admin key |
| `GATEKEEPER_JWT_SECRET` | API management and smart gateway | One shared, randomly generated signing secret of at least 32 characters |
| `GATEKEEPER_MANAGEMENT_URL` | Smart gateway | Management private URL, including the service port |
| `GATEKEEPER_GATEWAY_URL` | API management | Gateway public HTTPS URL |
| `GATEKEEPER_ANALYTICS_URL` | API management | Analytics private URL, including the service port |
| `GATEKEEPER_FRONTEND_ORIGIN` | API management | Exact frontend public origin, no trailing slash |
| `VITE_API_BASE` | Frontend | API management public HTTPS URL; set before frontend build |

The environment variable names for managed database products differ by provider. Map them to the names above rather than putting credentials in source code. Keep analytics private unless there is a separate reason to expose it; its health endpoint is unauthenticated, but its API is protected by the shared admin key.

## Hobby plan and resource usage

The eight application components (three Java services, frontend, MySQL, Redis, Kafka, and MongoDB) are below Hobby's current 50-service project limit. Railway's [pricing page](https://railway.com/pricing) lists Hobby at $5/month with $5 of included monthly usage, up to 48 vCPU/48 GB per service, six replicas, and volumes up to 5 GB. The included usage is a credit, **not** a guarantee that this always-on topology costs only $5: CPU, memory, volumes, and egress are usage-billed, and usage beyond the included amount is charged. Kafka and multiple databases may make the total exceed the credit, depending on sizing and uptime. Confirm current limits and estimated usage in Railway before deploying, and monitor billing during the first month.

This setup preserves the existing architecture; it does not consolidate or remove services to lower resource usage. If you need the monthly total to stay at the Hobby subscription price, measure a real deployment and reduce resource usage or use compatible external services as needed; do not assume all eight always-on services fit within the included usage.

Local development remains unchanged: the application properties still default to the Compose ports, frontend `.env` still points at local management, and `docker-compose.yml` continues to provide local MySQL, Redis, Kafka, and MongoDB.
