# storefront

Customer-facing shop for commerce-ops — Vite + React 18 + TypeScript + TanStack Query.

## Setup

```bash
npm install
npm run dev
```

Dev server: `http://localhost:5174`

## Config

| Variable | Default | Notes |
| --- | --- | --- |
| `VITE_API_BASE` | `http://localhost:8080` | api-gateway |
| `VITE_STOREFRONT_API_KEY` | `storefront-key` | Scoped key (`commerce.gateway.storefront.api-key`) |

## Flows

- `/` — catalog (`GET /api/store/catalog`)
- `/cart` — localStorage cart
- `/login`, `/register` — customer JWT auth
- `/account/addresses` — multi-address book (one default)
- `/checkout` — requires login; select saved address or add new
- `/orders/:id` — status polling (own orders only)
- `/account/orders` — order history
