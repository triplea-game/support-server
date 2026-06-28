# Authorization

Binary authorization for the support server:

- **Anonymous** (anyone) → read-only.
- **Member** (of one GitHub team) → read/write.

Enforced **server-side** in the app, with a reverse proxy (oauth2-proxy → GitHub) supplying
identity. The two layers are independent: the app rejects unauthorized writes even if the proxy is
misconfigured or absent (defense in depth).

---

## The identity contract

In production the app derives identity from two request headers, injected by nginx from the
oauth2-proxy auth subrequest:

| Header | Meaning |
|---|---|
| `X-Auth-Email` | The authenticated user's email. Absent/blank → anonymous. |
| `X-Auth-Groups` | Comma-separated groups/teams. Membership of `app.auth.member-group` grants write. |

Header names and the member group are configurable (`src/main/resources/application.properties`):

```properties
# Driven by the GITHUB_ADMIN_TEAM env var (see "make run" below); default is the real team.
app.auth.member-group=${GITHUB_ADMIN_TEAM:triplea-maps:mapadmins}
app.auth.email-header=X-Auth-Email
app.auth.groups-header=X-Auth-Groups
app.dev-fake-auth=${DEV_FAKE_AUTH:}
```

**Header sanitization is the security crux.** A browser must never be able to spoof identity by
sending its own `X-Auth-*` headers. nginx therefore *always overwrites* `X-Auth-Email` /
`X-Auth-Groups` on every route that reaches the app — from the auth subrequest when authenticated,
or to empty when anonymous. Client-supplied values are discarded.

---

## How identity is resolved (app)

Package `org.triplea.services.auth`:

| Type | Role |
|---|---|
| `Identity` | Value record: `email`, `groups`, `memberGroup`; `isAnonymous()`, `isMember()`. `ANONYMOUS` is the null-object default. |
| `IdentityProvider` | Interface `resolve(HeaderLookup)`; `HeaderLookup` is a minimal `String get(String)` accessor (framework-agnostic). |
| `HeaderIdentityProvider` | **Prod** — reads the `X-Auth-*` headers; no email → `ANONYMOUS`. |
| `DevFakeIdentityProvider` | **Dev** — synthesizes identity from `DEV_FAKE_AUTH` (`member`\|`anon`), ignoring headers. |
| `RequestIdentity` | Request-scoped resolver; inject it and call `get()` (memoized). Holds the source-selection gate. |

### The selection gate (prod-safety)

`RequestIdentity.select(...)` chooses the source. The rule:

- **Packaged production build (`LaunchMode.NORMAL`) ALWAYS uses the header provider**, even if
  `DEV_FAKE_AUTH=member` is set in the environment. This is the prod-safety guarantee — the
  worst-case failure ("everyone silently a member") cannot happen in prod.
- Otherwise (dev/test): use the dev provider when `DEV_FAKE_AUTH` is present, else the header
  provider.

Gating on `DEV_FAKE_AUTH` **presence** rather than the `%dev` profile is deliberate: both
`make dev` and `make run` run in `%dev`, so a profile gate would let dev-fake-auth clobber the real
proxy headers under `make run`.

> `Identity` is a final record, so it can't be a normal-scoped CDI bean (no proxy). Hence the
> `RequestIdentity` resolver instead of a `@Produces Identity`. Header access uses the injectable
> Vert.x `RoutingContext`, not `@Context HttpHeaders` (which is null in a plain CDI bean).

---

## Server-side enforcement

| Type | Role |
|---|---|
| `@RequiresMember` | `@NameBinding` annotation; apply to a JAX-RS class or method to gate it. |
| `MemberAuthFilter` | `@RequiresMember`-bound `ContainerRequestFilter`; aborts non-members with **401**. |

**401 for any non-member** (anonymous *or* authenticated-non-member) — a single contract that the
future HTMX work can map to a login redirect.

### CSRF protection

The identity cookie (oauth2-proxy's session) makes mutations forgeable: a malicious page could auto-
submit a form to a mutating endpoint and the browser would attach the victim's session cookie. Every
mutating browser form is therefore CSRF-protected with a **double-submit cookie**:

| Type | Role |
|---|---|
| `@CsrfProtected` | `@NameBinding`; apply to a JAX-RS class/method whose unsafe requests need a token. |
| `CsrfTokenProvider` | Request-scoped. Resolves the token once: the existing `csrf_token` cookie, or a freshly generated value. Exposes `token()` for templates and `isGenerated()`. |
| `CsrfCookieResponseFilter` | Emits the `Set-Cookie` when a fresh token was minted (typically the GET that renders a form). |
| `CsrfRequestFilter` | On POST/PUT/PATCH/DELETE, requires the `_csrf` form field to equal (constant-time) the `csrf_token` cookie, else **403**. Safe methods are not checked. |

The token is rendered into every form as a hidden `_csrf` field *and* set as the `csrf_token`
cookie. A forged cross-site POST can send the cookie (attached automatically) but cannot read it to
forge a matching field, so the two won't agree → 403. `CsrfRequestFilter` runs *after*
`MemberAuthFilter` (higher priority value), so a non-member to a gated form still gets the
authorization 401 rather than a 403.

The CSRF cookie is `HttpOnly` (no JS reads it — the field is server-rendered), `SameSite=Strict`,
and `Secure` in prod (`app.auth.csrf-cookie-secure`, `true` under `%prod`, `false` for local http).

> **SameSite decision.** The oauth2-proxy *session* cookie stays `SameSite=Lax`, **not** `Strict`:
> Strict would drop the cookie on the top-level redirect back from GitHub during the OAuth callback,
> breaking login. Lax alone is only a partial CSRF mitigation (it still allows top-level GET-initiated
> navigations), so the app-level double-submit token above is the actual defense, not the cookie's
> SameSite attribute. The CSRF cookie *itself* is `Strict` because it is never involved in any
> cross-site redirect — it is only ever echoed on our own same-site form posts.

Currently only `MapAttributesController` carries `@CsrfProtected` (it owns the only browser forms;
the game-client APIs are JSON, not cookie-authenticated). The status-page write controls, when
built, inherit the same machinery by adding `@CsrfProtected` and rendering `_csrf` into their forms.

### Route posture

| Route | Controller | Posture |
|---|---|---|
| `/support/maps/status` | `MapsStatusController` | **Public** (GET unannotated). Renders conditionally: read-only for anonymous; logout link + member region for members. |
| `/support/admin/map/attributes` | `MapAttributesController` | **Members-only** (`@RequiresMember` on the class → GET render + all 8 POSTs gated). |

The status page passes the resolved `Identity` to its template, which branches on
`identity.member` (write controls) and `!identity.anonymous` (logout link → `/oauth2/sign_out`).
The actual write controls and their member-only POST endpoints are a **separate story**, not yet
built; the template only has the seam.

---

## Local development

The server is always a single `quarkusDev` process; the only variable is whether the proxy sits in
front of it.

### `make dev` — no setup (newcomer default)

```bash
make dev                    # DEV_FAKE_AUTH=member: behave as a logged-in team member
DEV_FAKE_AUTH=anon make dev # behave as an anonymous (read-only) visitor
```

No proxy, no GitHub, no secrets. Browse Quarkus directly at <http://localhost:8080>.

### `make run` — real proxy overlay

Exercises the actual GitHub gate / login redirect / header sanitization locally.

```bash
make run   # docker compose -f docker-compose.auth.yml up -d  +  ./gradlew quarkusDev
```

Browse **nginx at <http://localhost:8000>** (not Quarkus at :8080). Prerequisite: a local
`.env.auth` (see below).

#### One-time setup

1. **Register a GitHub OAuth app** (<https://github.com/settings/developers>):
   - Homepage URL: `http://localhost:8000`
   - Authorization callback URL: `http://localhost:8000/oauth2/callback`
2. **Create `.env.auth`** from the template and fill it in:
   ```bash
   cp .env.auth.example .env.auth
   # set OAUTH2_PROXY_CLIENT_ID / _CLIENT_SECRET from the OAuth app
   # generate a cookie secret: openssl rand -base64 32 | tr -- '+/' '-_'
   ```
   `.env.auth` is gitignored (this is a public repo — secrets never go in git).

### The proxy overlay

`docker-compose.auth.yml` runs two host-network containers in front of the host's Quarkus process:

- **oauth2-proxy** (`:4180`) — GitHub provider; `read:org` scope to read team membership;
  restricted to the team in `$GITHUB_ADMIN_TEAM` (`<org>:<team-slug>`, e.g. `triplea-maps:mapadmins`);
  emits identity on the auth subrequest.
- **nginx** (`:8000`, `auth/nginx.conf`) — terminates browser traffic and:
  - **optional-auth** on `/support/maps/status` (anonymous allowed; identity forwarded if present),
  - **hard-gate** on `/support/admin/` (unauthenticated → redirect to login),
  - **header sanitization** everywhere (always overwrites `X-Auth-*`).

> **One env var keeps the proxy gate and the app check aligned.** oauth2-proxy both *accepts*
> (`OAUTH2_PROXY_GITHUB_TEAM`) and *emits* (`X-Auth-Request-Groups`) the same `<org>:<team-slug>`
> form, so a single value — `GITHUB_ADMIN_TEAM` (default `triplea-maps:mapadmins`) — feeds both the
> proxy and `app.auth.member-group`. The slug is GitHub's lowercased team slug (the team
> *name* `MapAdmins` has slug `mapadmins`), not the display name. `make run` exports the var to both
> the compose overlay and `quarkusDev`; override it in the shell to point at a different team.

### Verifying header sanitization

Header sanitization (nginx always overwriting `X-Auth-*`) is the security crux, and it lives in the
proxy, not the app — so it can't be a `@QuarkusTest`. `auth/verify-header-sanitization.sh` checks it
end-to-end against the running overlay:

```bash
make run                  # in one terminal: proxy + quarkusDev
make verify-auth-headers  # in another: brings the proxy up (idempotent) and runs the check
```

It proves the property by contrast: the app directly (`:8080`) *trusts* a spoofed `X-Auth-Email`
(expected — that confirms the header is wired through and that the proxy is the thing protecting it),
while the same spoofed header through nginx (`:8000`) is stripped so the app renders the anonymous
view. It also asserts a spoofed header on `/support/admin` does not yield a 200 (login redirect
instead).

> The app still runs on the host via `quarkusDev`, so this isn't yet fully self-contained. The
> intended direction is to containerize the app and drive the whole stack from docker compose, making
> this a CI-runnable integration test rather than a manual-stack check.

---

## Production deploy

The production reverse proxy (nginx + oauth2-proxy, vault-managed secrets, stable cookie secret,
HTTPS/TLS, prod GitHub OAuth app callback) is managed in the separate **`infrastructure`** repo,
not here. It mirrors `auth/nginx.conf`'s optional-auth + hard-gate + header-sanitization rules.

---

## Key files

| File | Purpose |
|---|---|
| `src/main/java/org/triplea/services/auth/` | Identity, providers, resolver, `@RequiresMember`, filter; `@CsrfProtected` + the CSRF provider/filters. |
| `src/main/resources/application.properties` | Header names, member group, `DEV_FAKE_AUTH` wiring, `app.auth.csrf-cookie-secure`. |
| `src/main/resources/templates/MapsStatusController/statusPage.html` | Conditional render + logout link. |
| `docker-compose.auth.yml`, `auth/nginx.conf`, `.env.auth.example` | Local real-proxy overlay. |
| `auth/verify-header-sanitization.sh` | End-to-end header-sanitization check (`make verify-auth-headers`). |
| `src/testInteg/java/org/triplea/services/maps/**/Map*AuthIntegrationTest.java` | The three auth states. |
