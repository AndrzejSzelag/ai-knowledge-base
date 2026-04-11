# Security

## Authentication

Google OAuth2 via Spring Security. Session-based (not JWT).

| Endpoint                                 | Access             |
| ---------------------------------------- | ------------------ |
| `/api/ai/ask`, `/api/ai/ask-streaming`   | Public             |
| `/`, `/index.html`, `/assets/**`         | Public (static)    |
| `/api/auth/**`, `/oauth2/**`             | Public (auth flow) |
| `/api/knowledge/**`, `/api/documents/**` | Authenticated      |
| Everything else                          | Authenticated      |

**Flow:**

1. Frontend redirects to `/oauth2/authorization/google`
2. Spring Security handles the OAuth2 handshake
3. On success → redirect to `${app.frontend-url}/`
4. On failure → redirect to `${app.frontend-url}/login?error=true`
5. Session stored server-side; `JSESSIONID` cookie sent to browser

**`RestAuthenticationEntryPoint`** distinguishes unauthenticated requests:

- `/api/**` → `401 Unauthorized` (no browser login prompt)
- Web routes → redirect to `/`

**`AuthController` (`GET /api/auth/me`)** returns the authenticated user's Google profile (name, email, picture, given/family name) or `401` if not logged in. Frontend (`AuthContext.jsx`) calls this on app load to restore session state.

---

## CSRF

`CookieCsrfTokenRepository` with `HttpOnly=false` - required for SPA to read the token via JavaScript and attach it as `X-XSRF-TOKEN` header.

CSRF is **disabled** for:

- `/api/ai/ask`, `/api/ai/ask-streaming` (public, stateless endpoints)
- Static resources

---

## CORS

Restricted to `${app.frontend-url}` only. Allowed methods: `GET POST PUT DELETE OPTIONS`. Allowed headers include `X-XSRF-TOKEN` and `Last-Event-ID` (SSE). Credentials allowed. Preflight cached for 1 hour.

---

## Session Management

- Policy: `IF_REQUIRED` — session created only when needed
- Session fixation: `migrateSession()` — new session ID on login
- Logout: invalidates session, deletes `JSESSIONID` and `XSRF-TOKEN` cookies

---

## Rate Limiting

Bucket4j token bucket, per IP, on all `/api/ai/*` endpoints: **20 requests/min**. Exceeding the limit returns `429 Too Many Requests` with `Retry-After: 60`.

---

## Security Headers

| Header                      | Value                                 |
| --------------------------- | ------------------------------------- |
| `X-Frame-Options`           | `DENY`                                |
| `X-Content-Type-Options`    | `nosniff`                             |
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains` |
| `Referrer-Policy`           | `strict-origin-when-cross-origin`     |

---

## Data & Model Isolation

- No data leaves your infrastructure - all AI processing runs locally via Ollama
- LLM system prompt enforces strict context boundaries - model answers only from ingested data
- `VectorStoreEntity` is annotated `@Immutable` - Hibernate will never issue an accidental `UPDATE`

---

← [Back to README](../README.md)
