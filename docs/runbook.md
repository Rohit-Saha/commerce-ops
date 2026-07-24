# Runbook

How to bring the whole stack up locally and drive a few demo scenarios
through it.

## 1. Start infrastructure

```bash
docker compose up -d
```

This starts Postgres (`5433`, seeded with `order_db`, `inventory_db`,
`payment_db`, `shipping_db`, `saga_db`, `returns_db`, `customer_db`,
`catalog_db`, `invoice_db`, `strapi_db` via
`scripts/init-databases.sql`), Redis (`6379`), Kafka (`9092`), Jaeger
(`16686`, OTLP on `4317`/`4318`), the OTel collector (OTLP on
`4319`/`4320`), Prometheus (`9090`), Grafana (`3001`), Elasticsearch
(`9200`), Strapi (`1337`), and **Keycloak** (`8180`, realm `commerce-ops`).

Check everything is healthy:

```bash
docker compose ps
```

First Strapi boot: open `http://localhost:1337/admin` and create an admin
user. Seeded Product entries use the same SKUs as inventory
(`SKU-TEE-001`, `SKU-MUG-001`, `SKU-HAT-001`) and are published so
`catalog-service` can merge them into Elasticsearch.
## 2. Build the services

```bash
mvn -q -DskipTests package
```

This builds all shared libraries and every backend module (including the
`returns-service` Phase 2 stub) and produces a runnable fat jar under each
module's `target/` directory.

## 3. Start each service

Either use the helper script, or start each jar by hand.

### Option A: helper script

```bash
scripts/start-services.sh
# or bring infra + Auth BFF (Keycloak) + rebuild in one shot:
scripts/start-services.sh --infra --oidc --build
```

This starts every backend service (including **config-server** on **8888** and
**invoice-service** on **8089**) in the background with logs in
`logs/<service>.log` and pid files under `logs/pids/`.

| Flag | Effect |
|------|--------|
| `--infra` / `-i` | `docker compose up -d` and wait for Postgres/Redis/Kafka (and Keycloak if `--oidc`) |
| `--oidc` / `-o` | Auth BFF: set `COMMERCE_SECURITY_MODE=oidc`, `SPRING_PROFILES_ACTIVE=oidc`, issuer + BFF client secrets; require Redis + Keycloak |
| `--build` / `-b` | `mvn -q -DskipTests package` before starting |
| service names | Start only those jars, e.g. `api-gateway payment-service` |

`COMMERCE_SECURITY_MODE=oidc` in the environment or `.env.local` also enables OIDC
without passing `--oidc`.

Stop JVM services with:

```bash
scripts/stop-services.sh
scripts/stop-services.sh --infra   # also docker compose stop
scripts/stop-services.sh --all     # jars + docker compose down (volumes kept)
```

Pass specific service names as arguments to start only a subset, e.g.
`scripts/start-services.sh payment-service shipping-service`.

### Option B: by hand

```bash
java -jar services/api-gateway/target/api-gateway-*.jar           --server.port=8080
java -jar services/order-service/target/order-service-*.jar       --server.port=8081
java -jar services/inventory-service/target/inventory-service-*.jar --server.port=8082
java -jar services/payment-service/target/payment-service.jar     --server.port=8083
java -jar services/shipping-service/target/shipping-service.jar   --server.port=8084
java -jar services/saga-orchestrator/target/saga-orchestrator-*.jar --server.port=8085
java -jar services/returns-service/target/returns-service.jar     --server.port=8086
java -jar services/customer-service/target/customer-service.jar   --server.port=8087
java -jar services/catalog-service/target/catalog-service.jar     --server.port=8088
java -jar services/invoice-service/target/invoice-service.jar     --server.port=8089
```

> The admin UI and the demo `curl` commands below all go through
> `api-gateway` on port 8080, which proxies to each backend service using
> the base URLs in its `application.yml` (`commerce.gateway.services.*`).
> Every `/api/**` request to the gateway requires an `X-API-Key: dev-admin-key`
> header (except `POST /api/auth/login` and `POST /api/payments/webhooks/razorpay`).
> See `docs/architecture.md`.
>
> JSON responses use a shared envelope (`success`, `message`, `data`, `meta`).
> For `curl | jq` demos, read the payload with `.data` (for example
> `jq '.data'` or `jq '.data.id'`). Errors put the customer-facing text in
> `.message`.

## 4. Start the admin UI

```bash
cd admin-ui && npm install && npm run dev
```

Serves the React console on `http://localhost:5173` (Vite dev server). It
talks only to `api-gateway` (`VITE_API_BASE`, default `http://localhost:8080`).
Open the UI and sign in with the gateway admin credentials (defaults
`admin` / `admin` from `commerce.gateway.admin`). After login the UI stores
the API key in `sessionStorage` and sends it on subsequent requests.

## 4b. Start the customer storefront

```bash
cd storefront && npm install && npm run dev
```

Serves the shop on `http://localhost:5174`. It uses the scoped storefront API
key (`VITE_STOREFRONT_API_KEY`, default `storefront-key`, matching
`commerce.gateway.storefront.api-key`) plus a customer JWT from
register/login. Catalog and cart are public; checkout, order status, address
book, and order history require login. That key cannot call admin-only
endpoints (inventory writes, chaos, sagas, SSE, etc.).

Typical storefront path: register → add addresses (one default) → cart →
checkout (Razorpay authorize in INR, then place order) → order history →
download tax invoice PDF after shipment completes.

## Invoices (GST-style PDF)

`invoice-service` (:8089) listens for `ShipmentCreated` (not a saga step). It
issues a GST-style tax invoice when the label is booked. Later shipment
tracking statuses do not re-trigger invoicing.

### Shipping (simulated carrier)

After payment, the order stays **`PAID`** until an admin books a shipment:

```bash
curl -s -X POST "$API/api/shipments" -H "$KEY" -H "Content-Type: application/json" \
  -d "{\"orderId\":\"$ORDER_ID\"}" | jq .
```

That publishes `ShipmentCreated` (saga completes, order → `SHIPPING`, invoice).
Walk tracking with admin Advance (order becomes `DELIVERED` on the last step):

```bash
curl -s -X POST "$API/api/shipments/$SHIPMENT_ID/advance" -H "$KEY" | jq .
```

Statuses: `CREATED` → `PICKED_UP` → `IN_TRANSIT` → `OUT_FOR_DELIVERY` → `DELIVERED`.

Customers can cancel from the storefront while the order is still **`PAID`**
(before create). Cancel after ship is not supported in this phase.

Shiprocket remains available but dormant (`SHIPPING_PROVIDER=shiprocket` plus
`SHIPROCKET_*` env vars); not required for local demos.

Invoice issuance still listens for `ShipmentCreated`. The service then
loads the order + payment refs, builds a tax invoice with **tax-inclusive**
catalog prices at **18% GST**, and stores a PDF.

- Intra-state (buyer ship-to state matches seller `Karnataka` / `KA`): CGST + SGST.
- Inter-state: IGST.
- Seller details: `commerce.invoice.seller.*` in invoice-service `application.yml`.

If Postgres was already initialized before this phase, create the DB once:

```bash
docker compose exec postgres psql -U commerce -c "CREATE DATABASE invoice_db;"
```

After a completed order:

```bash
# JSON
curl -s "$API/api/invoices/by-order/$ORDER_ID" -H "$KEY" | jq .

# PDF
curl -s -OJ "$API/api/invoices/$INVOICE_ID/pdf" -H "$KEY"
```

Admin order detail and storefront order status show a Download invoice control
once the invoice exists.

## Payments: simulated vs Razorpay

Currency is **INR** end-to-end (storefront/admin formatters use `en-IN`;
catalog prices are in rupees).

`payment-service` selects the provider with `PAYMENT_PROVIDER`
(`simulated` default, or `razorpay`):

| Mode | Checkout | Capture | Refund | Chaos |
|------|----------|---------|--------|-------|
| `simulated` (default) | Storefront skips Checkout.js and posts a simulated authorize | Instant on `CapturePayment` | Local status flip | Yes (`.99`, `FAIL` currency, failure-rate) |
| `razorpay` | Razorpay Checkout.js authorizes (`payment_capture=0`) | Saga capture calls Razorpay capture after inventory reserve | Razorpay refund API | Chaos knobs ignored |

### Enable Razorpay (test keys)

1. Create a Razorpay test account and copy **Key Id** / **Key Secret**.
2. Optionally create a webhook pointing at
   `http://<host>:8080/api/payments/webhooks/razorpay` (signature verified with
   webhook secret; no API key required on this path).
3. Export before starting services (or set in your shell profile):

```bash
export PAYMENT_PROVIDER=razorpay
export RAZORPAY_KEY_ID=rzp_test_...
export RAZORPAY_KEY_SECRET=...
export RAZORPAY_WEBHOOK_SECRET=...   # optional but recommended
```

4. Storefront: set `VITE_RAZORPAY_KEY_ID` to the same public key id in
   `storefront/.env` (documentation only — Checkout uses the `keyId` returned
   by `POST /api/payments/razorpay/orders`).

Authorize → capture flow:

1. Storefront `POST /api/payments/razorpay/orders` → Razorpay order (manual capture).
2. Customer pays in Checkout.js → payment authorized.
3. Storefront `POST /api/orders` with `razorpayOrderId` / `razorpayPaymentId` /
   `razorpaySignature` → gateway creates a deferred order, verifies signature,
   records `AUTHORIZED`, then publishes `OrderCreated`.
4. Saga reserves inventory, then `CapturePayment` → payment-service captures
   the authorized Razorpay payment → `PaymentCaptured`.
5. Compensation `RefundPayment` → Razorpay refund → `PaymentRefunded`.

Admin demo flows (`POST /api/orders` with the admin key) still use **simulated
instant capture** when `PAYMENT_PROVIDER=simulated`. With `razorpay`, admin
orders that skip authorize will fail capture until you wire an authorize step.

### Storefront checkout with simulated provider

With the default `PAYMENT_PROVIDER=simulated`, “Pay & place order” still calls
`/api/payments/razorpay/orders`, then auto-authorizes with a synthetic payment
id (no Checkout modal). Useful for local demos without Razorpay credentials.


| Tool | URL | Notes |
|------|-----|-------|
| Admin UI | http://localhost:5173 | Sign in `admin` / `admin`; orders, demo flows, inventory, sagas |
| Storefront | http://localhost:5174 | Catalog → login → addresses → checkout → history |
| Grafana | http://localhost:3001 | login `admin` / `admin` |
| Jaeger | http://localhost:16686 | Search by service name or trace id |
| Prometheus | http://localhost:9090 | Raw `/actuator/prometheus` scrape targets |

## 6. Demo flows

All commands go through `api-gateway` (`:8080`) with the default admin API
key, and assume the services are running as in step 3.

```bash
API=http://localhost:8080
KEY="X-API-Key: dev-admin-key"
SFKEY="X-API-Key: storefront-key"
```

### Customer auth + multi-address checkout

```bash
# Register
REG=$(curl -s -X POST $API/api/customers/register \
  -H "Content-Type: application/json" -H "$SFKEY" \
  -d '{"email":"alice@example.com","password":"password123","displayName":"Alice"}')
TOKEN=$(echo "$REG" | jq -r .token)
AUTH="Authorization: Bearer $TOKEN"

# Save two addresses; second as default
curl -s -X POST $API/api/customers/me/addresses \
  -H "Content-Type: application/json" -H "$SFKEY" -H "$AUTH" \
  -d '{"recipientName":"Alice","line1":"1 Main St","city":"Austin","state":"TX","postalCode":"78701","country":"US"}'

ADDR=$(curl -s -X POST $API/api/customers/me/addresses \
  -H "Content-Type: application/json" -H "$SFKEY" -H "$AUTH" \
  -d '{"recipientName":"Alice","line1":"99 Lake Rd","city":"Austin","state":"TX","postalCode":"78702","country":"US","isDefault":true}')
ADDR_ID=$(echo "$ADDR" | jq -r .id)

# Simulated Razorpay authorize session (PAYMENT_PROVIDER=simulated)
RZP=$(curl -s -X POST $API/api/payments/razorpay/orders \
  -H "Content-Type: application/json" -H "$SFKEY" -H "$AUTH" \
  -d '{"amount":1299.00,"currency":"INR","receipt":"demo-1"}')
RZP_ORDER=$(echo "$RZP" | jq -r .razorpayOrderId)
RZP_PAY="pay_sim_demo1"

# Checkout with selected address + Razorpay refs (JWT required; customerId injected by gateway)
curl -s -X POST $API/api/orders \
  -H "Content-Type: application/json" -H "$SFKEY" -H "$AUTH" \
  -H "Idempotency-Key: sf-checkout-1" \
  -d "{\"currency\":\"INR\",\"shippingAddressId\":\"$ADDR_ID\",\"lines\":[{\"sku\":\"SKU-TEE-001\",\"quantity\":1,\"unitPrice\":1299.00}],\"razorpayOrderId\":\"$RZP_ORDER\",\"razorpayPaymentId\":\"$RZP_PAY\",\"razorpaySignature\":\"simulated\"}"

# Order history
curl -s $API/api/orders/mine -H "$SFKEY" -H "$AUTH" | jq .

# Unauthenticated checkout → 401
curl -s -o /dev/null -w "%{http_code}\n" -X POST $API/api/orders \
  -H "Content-Type: application/json" -H "$SFKEY" \
  -d '{"currency":"INR","lines":[{"sku":"SKU-TEE-001","quantity":1,"unitPrice":1299.00}]}'
```

IDOR check: register a second customer, then `GET /api/orders/{aliceOrderId}`
with Bob's JWT should return `404`.

### Catalog projection (inventory → Kafka → catalog-service) + Strapi + ES

Storefront catalog is served from `catalog-service` (`GET /api/store/catalog`,
`/search`, `/by-slug/{slug}`, `/categories`). Inventory remains SoT for name,
price, and qty; Strapi is SoT for merchandising (title, slug, copy, gallery,
category, tags, draft/publish). Only **published** Strapi products that also
exist in inventory (and are not soft-deleted) are indexed in Elasticsearch
and shown on the storefront.

```bash
# Create a product in inventory (admin key)
curl -s -X POST $API/api/inventory \
  -H "Content-Type: application/json" -H "$KEY" \
  -d '{"sku":"SKU-CAT-1","name":"Catalog Tee","unitPrice":799.00,"availableQty":5}'

# It will NOT appear on the storefront until a matching Product is published in Strapi
# (Admin → Content Manager → Product, sku = SKU-CAT-1, Publish).
# Or use a seeded SKU after Strapi sync:

sleep 2
curl -s "$API/api/store/catalog/search?q=tee" -H "$SFKEY" | jq .
curl -s "$API/api/store/catalog/by-slug/classic-tee" -H "$SFKEY" | jq .
curl -s "$API/api/store/catalog/categories" -H "$SFKEY" | jq .

# Restock and confirm qty updates in search without losing CMS fields
curl -s -X POST "$API/api/inventory/SKU-TEE-001/restock?qty=3" -H "$KEY"
sleep 2
curl -s "$API/api/store/catalog/search?q=classic" -H "$SFKEY" | jq '.items[0] | {sku, availableQty, displayTitle}'

# Unpublish in Strapi → product disappears from search (drafts are never indexed)
# Soft-delete / inventory delete → removed from ES even if Strapi stays published
```

Strapi Admin: `http://localhost:1337/admin` (also linked from admin-ui sidebar).
Elasticsearch: `http://localhost:9200`. Catalog webhook secret default:
`commerce-ops-strapi-webhook` (`X-Webhook-Secret`).
### Happy path (admin demo — address optional)

```bash
curl -s -X POST $API/api/orders \
  -H "Content-Type: application/json" -H "$KEY" \
  -H "Idempotency-Key: demo-happy-1" \
  -d '{
    "customerId": "cust-1",
    "currency": "INR",
    "lines": [{ "sku": "SKU-TEE-001", "quantity": 2, "unitPrice": 1299.00 }]
  }'
```

Watch it progress through the saga:

```bash
curl -s $API/api/sagas -H "$KEY" | jq .
```

It should move `STARTED → RESERVING → RESERVED → PAYING → PAID → SHIPPING →
COMPLETED`. Open Jaeger and search for the `order-service` trace with this
order's id to see every hop across all four services, or open the admin UI
(`http://localhost:5173`) to watch the order update live over the
`/api/stream/orders` SSE feed.

### Payment failure (chaos)

Chaos applies only when `PAYMENT_PROVIDER=simulated`. Either trigger the
deterministic `.99` rule by using an amount that ends in `.99`:

```bash
curl -s -X POST $API/api/orders \
  -H "Content-Type: application/json" -H "$KEY" \
  -H "Idempotency-Key: demo-fail-1" \
  -d '{
    "customerId": "cust-1",
    "currency": "INR",
    "lines": [{ "sku": "SKU-TEE-001", "quantity": 1, "unitPrice": 19.99 }]
  }'
```

...or dial in a random failure rate for every subsequent capture:

```bash
curl -s -X POST "$API/api/payments/chaos?failureRate=0.5" -H "$KEY"
```

...or use an order id prefixed `FAIL-` if you are calling `payment-service`
directly with a synthetic command. In every case, watch the saga compensate:
`PAYING → COMPENSATING (RELEASE_INVENTORY) → COMPENSATED`, and the inventory
reservation made in step 1 gets released back to stock. Reset the chaos knob
afterwards with `failureRate=0.0`.

Shipment failures work the same way via `NOSHIP-`-prefixed order ids or
`POST $API/api/shipments/chaos?failureRate=0.5`, and compensate by refunding
payment then releasing inventory. After a successful simulated booking you can
walk tracking with:

```bash
curl -s -X POST "$API/api/shipments/$SHIPMENT_ID/advance" -H "$KEY" | jq .
```

### Rate limiting

`api-gateway` throttles write methods (POST/PUT/PATCH/DELETE) under
`/api/**` to `commerce.gateway.rate-limit.limit` requests per
`window-seconds` (default **30 requests / 60s**), keyed by the `X-API-Key`
header. Blow through it with a burst of order creations:

```bash
for i in $(seq 1 35); do
  curl -s -o /dev/null -w "%{http_code} " -X POST $API/api/orders \
    -H "Content-Type: application/json" -H "$KEY" \
    -H "Idempotency-Key: demo-burst-$i" \
    -d '{"customerId":"cust-1","currency":"INR","lines":[{"sku":"SKU-TEE-001","quantity":1,"unitPrice":499.00}]}'
done; echo
```

The first ~30 requests return `201`; the rest return `429 Too Many Requests`
with a `Retry-After` header until the 60s window rolls forward. Inspect the
limiter's own bookkeeping via `X-RateLimit-Limit` / `X-RateLimit-Remaining`
on any single response.

`order-service`'s `Idempotency-Key` header is a separate, complementary
mechanism worth demoing too -- replaying the exact same request with an
already-seen key returns the original order instead of creating a
duplicate (and doesn't count twice against inventory):

```bash
# Re-send the exact same request/idempotency key as the happy-path example above
curl -s -X POST $API/api/orders \
  -H "Content-Type: application/json" -H "$KEY" \
  -H "Idempotency-Key: demo-happy-1" \
  -d '{
    "customerId": "cust-1",
    "currency": "INR",
    "lines": [{ "sku": "SKU-TEE-001", "quantity": 2, "unitPrice": 1299.00 }]
  }'
# -> same order id as before, no second saga is started
```

### Circuit breakers (Resilience4j)

`api-gateway` wraps every downstream proxy call in a named circuit breaker
(`order`, `inventory`, `payment`, …). Defaults (override in
`services/api-gateway/.../application.yml`):

| Knob | Default | Meaning |
|------|---------|---------|
| `slidingWindowSize` | 20 | Calls counted in the closed-state window |
| `minimumNumberOfCalls` | 10 | Min samples before failure-rate applies |
| `failureRateThreshold` | 50 | % failures → OPEN |
| `waitDurationInOpenState` | 10s | Cool-down before HALF_OPEN probes |
| `permittedNumberOfCallsInHalfOpenState` | 3 | Probes allowed while recovering |

Recorded failures: `ResourceAccessException`, `HttpServerErrorException`,
`IOException`. Ignored: `HttpClientErrorException` (4xx).

**Demo — trip the order breaker**

```bash
# Stop order-service (leave gateway + other services up)
# Then hammer list orders until the circuit opens:
for i in $(seq 1 25); do
  curl -s -o /dev/null -w "%{http_code} " "$API/api/orders" -H "$KEY"
done; echo
# Early calls: 503 (unreachable). After threshold: still 503, but immediate
# (CallNotPermitted) with message "Service temporarily unavailable; try again shortly."

# Metrics (gateway actuator):
curl -s http://localhost:8080/actuator/prometheus | grep resilience4j_circuitbreaker_state
# Look for name="order" state OPEN (gauge value encoding CLOSED/OPEN/HALF_OPEN)

# Restart order-service; after waitDurationInOpenState, a few HALF_OPEN probes
# succeed and the breaker returns to CLOSED.
```

Payment-service (`razorpay`) and shipping-service (`shiprocket`, plus
`order`/`saga` clients) expose the same metric family on their own
`/actuator/prometheus` endpoints.

### Service discovery (EKS / Kubernetes DNS)

There is **no Eureka or Consul**. On EKS (or any Kubernetes cluster),
callers resolve peers via **ClusterIP Service DNS**:

| Logical service | Canonical DNS name | Port |
|-----------------|--------------------|------|
| config-server | `config-server` | 8888 |
| api-gateway | `api-gateway` | 8080 |
| order-service | `order-service` | 8081 |
| inventory-service | `inventory-service` | 8082 |
| payment-service | `payment-service` | 8083 |
| shipping-service | `shipping-service` | 8084 |
| saga-orchestrator | `saga-orchestrator` | 8085 |
| returns-service | `returns-service` | 8086 |
| customer-service | `customer-service` | 8087 |
| catalog-service | `catalog-service` | 8088 |
| invoice-service | `invoice-service` | 8089 |

**Local (default):** JVMs on the host keep `http://localhost:808x` (see
`scripts/start-services.sh`). Gateway also accepts env overrides such as
`ORDER_SERVICE_URL`.

**In-cluster:** set `SPRING_PROFILES_ACTIVE=k8s` so each service loads
`application-k8s.yml` (Service DNS for HTTP peers; `postgres` / `redis` /
`kafka` for data plane). Manifests: `deploy/k8s/`. Clients also reach
Config Server at `http://config-server:8888` via `CONFIG_SERVER_URL`.

```bash
# After applying deploy/k8s (with images + infra Services present):
kubectl -n commerce-ops exec deploy/api-gateway -- \
  curl -sf http://order-service:8081/actuator/health
# Scale without changing gateway config:
kubectl -n commerce-ops scale deploy/order-service --replicas=2
```

### Config Server (optional)

`scripts/start-services.sh` starts **config-server** first (port **8888**)
and points it at the monorepo `config-repo/` via
`CONFIG_SERVER_SEARCH_LOCATIONS`. Every other service imports:

`optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}`

so the stack still runs if Config Server is stopped.

```bash
# Inspect served config (JSON/property sources):
curl -s http://localhost:8888/api-gateway/default | jq .
curl -s http://localhost:8888/api-gateway/k8s | jq .

# Demo override: edit config-repo/api-gateway.yml (e.g. rate-limit.limit),
# then restart api-gateway to pick up the change (no live bus in v1).
```

### Production security (OIDC / Keycloak Auth BFF)

Default local mode is **`legacy`** (API keys). To run the production-shaped path
(frontends never call Keycloak — only api-gateway does):

```bash
# One-shot (recommended):
scripts/start-services.sh --infra --oidc --build

# Equivalent manual env:
docker compose up -d keycloak redis   # Keycloak :8180; Redis for Spring Session
export COMMERCE_SECURITY_MODE=oidc
export SPRING_PROFILES_ACTIVE=oidc
export OAUTH2_ISSUER_URI=http://localhost:8180/realms/commerce-ops
# Optional overrides (defaults match realm import secrets for local only):
# export KEYCLOAK_ADMIN_UI_BFF_SECRET=admin-ui-bff-secret
# export KEYCLOAK_STOREFRONT_BFF_SECRET=storefront-bff-secret
# export BFF_ADMIN_FRONTEND_URL=http://localhost:5173
# export BFF_STOREFRONT_FRONTEND_URL=http://localhost:5174
scripts/start-services.sh
# Admin UI / storefront:
# VITE_SECURITY_MODE=oidc in .env (no VITE_KEYCLOAK_* required)
```

| Check | Expect |
|-------|--------|
| Browser Network tab | No calls to `:8180` / Keycloak from admin-ui or storefront |
| Admin login | Redirect `GET /api/auth/login?client=admin-ui` → Keycloak → `/api/auth/callback/admin-ui-bff` → `COMMERCE_SESSION` cookie → UI |
| Storefront `customer1`/`customer1` | Same with `client=storefront`; place order; ownership enforced |
| APIs with cookie | `GET /api/orders` works with `credentials: 'include'` (no Bearer in browser) |
| Logout | `GET /api/auth/logout?client=...` clears session (+ Keycloak end_session) |
| Missing/invalid session | `401` |
| Customer session on `/api/sagas` | `403` |
| Legacy mode | Still works when `COMMERCE_SECURITY_MODE=legacy` |
| Razorpay webhook without signature (live + secret set) | rejected |
| Ingress | Only gateway (+ optional Keycloak admin); domain Services stay ClusterIP |

**Network boundary:** public traffic → Ingress → `api-gateway` only. Downstream
services and Config Server are ClusterIP. Actuator sensitive endpoints are
denied in OIDC mode except `health` / `info` / `prometheus`.

**Secrets inventory (never commit real values):** `ADMIN_*`, `STOREFRONT_API_KEY`,
`CUSTOMER_JWT_SECRET` (legacy), `OAUTH2_ISSUER_URI`, `KEYCLOAK_ADMIN_UI_BFF_SECRET`,
`KEYCLOAK_STOREFRONT_BFF_SECRET`, `KEYCLOAK_ADMIN*`, `RAZORPAY_WEBHOOK_SECRET`,
`SHIPROCKET_WEBHOOK_TOKEN`, DB passwords. Set `REQUIRE_WEBHOOK_SECRETS=true` in
k8s so blank Shiprocket tokens fail closed.
Audit lines: logger `commerce.audit` (`event=auth_*` / `event=mutation`).
