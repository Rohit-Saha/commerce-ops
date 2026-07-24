# admin-ui

Ops console for commerce-ops — Vite + React 18 + TypeScript + TanStack Query + react-router-dom.

## Setup

```bash
npm install
npm run dev
```

The dev server runs on `http://localhost:5173` (matches the CORS config in the backend services).

## Config

Environment variables (see `.env` / `.env.example`):

| Variable        | Default                 | Notes                                   |
| --------------- | ------------------------ | ---------------------------------------- |
| `VITE_API_BASE` | `http://localhost:8080` | Base URL for the API gateway |

## Auth

Dual mode via `VITE_SECURITY_MODE`:

- **`legacy` (default):** sign-in with **admin** / **admin**. Gateway returns the admin
  API key; the UI stores it in `sessionStorage` and sends `X-API-Key` (and `?apiKey=` for SSE).
- **`oidc`:** browser redirects to gateway BFF `GET /api/auth/login?client=admin-ui`
  (never talks to Keycloak). Session is an httpOnly cookie (`COMMERCE_SESSION`);
  API calls use `credentials: 'include'`. See [`SSE_AUTH.md`](./SSE_AUTH.md).

## Pages

- `/login` — Admin sign-in
- `/` — Orders list
- `/orders/:id` — Order detail
- `/demo` — Preset saga scenarios, chaos knobs, and custom order builder
- `/inventory` — Stock levels with create / edit / restock / soft-delete
- `/sagas` — Saga instances table

## Live updates

Orders and Order detail subscribe to `GET /api/stream/orders` via `EventSource` and invalidate
the relevant TanStack Query caches on every message. Auth for the stream is documented in
[`SSE_AUTH.md`](./SSE_AUTH.md) (legacy `?apiKey=` or OIDC session cookie with `withCredentials`).

## Scripts

- `npm run dev` — start the Vite dev server
- `npm run build` — type-check and build for production
- `npm run preview` — preview the production build locally
- `npm run lint` — run ESLint
