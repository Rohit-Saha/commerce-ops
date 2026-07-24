# SSE authentication

`EventSource` cannot set custom HTTP headers. Auth therefore uses cookies or query params.

## Legacy mode (`commerce.security.mode=legacy`)

Admin UI passes the API key as `?apiKey=` on `/api/stream/orders`.
`ApiKeyAuthFilter` accepts that query param (in addition to `X-API-Key`).

## OIDC mode (`commerce.security.mode=oidc`) — Auth BFF

Frontends never talk to Keycloak. After BFF login the gateway sets an httpOnly
**`COMMERCE_SESSION`** cookie (Spring Session → Redis) that holds access/refresh tokens server-side.

1. Admin UI opens `EventSource(url, { withCredentials: true })` so the session cookie is sent.
2. Gateway `BearerTokenResolver` loads the access token from the Redis session (after optional refresh).
3. `POST /api/auth/sse-cookie` and `?access_token=` remain as legacy fallbacks only; prefer cookie session.

Do **not** put long-lived tokens in query strings on public networks without TLS.
