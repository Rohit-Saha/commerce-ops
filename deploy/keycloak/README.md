# Keycloak (local OIDC IdP)

Realm import: `commerce-ops-realm.json` (imported on first `start-dev --import-realm`).

Browsers never call Keycloak when apps use **Auth BFF** mode (`VITE_SECURITY_MODE=oidc`):
admin-ui / storefront redirect to `api-gateway` `/api/auth/login`; the gateway uses the
confidential clients below to complete Authorization Code and store tokens in Redis session.

| Item | Value |
|------|--------|
| URL | http://localhost:8180 |
| Realm | `commerce-ops` |
| Admin console | http://localhost:8180 (KEYCLOAK_ADMIN / KEYCLOAK_ADMIN_PASSWORD, default admin/admin) |
| Public SPA clients (legacy / optional) | `admin-ui`, `storefront` |
| Confidential BFF clients | `admin-ui-bff` (secret `admin-ui-bff-secret`), `storefront-bff` (secret `storefront-bff-secret`) |
| BFF redirect URIs | `http://localhost:8080/api/auth/callback/admin-ui-bff`, `.../storefront-bff` |
| Resource | `api-gateway` (bearer-only) |
| Roles | `admin`, `customer` |
| Demo users | `admin` / `admin` (role admin); `customer1` / `customer1` (role customer, claim `customer_id=cust-1`) |

Issuer for the gateway (host JVM):

`http://localhost:8180/realms/commerce-ops`

In-cluster issuer:

`http://keycloak:8080/realms/commerce-ops`

Override BFF client secrets via `KEYCLOAK_ADMIN_UI_BFF_SECRET` /
`KEYCLOAK_STOREFRONT_BFF_SECRET` (required under `k8s`/`prod` profiles).
