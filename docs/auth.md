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
app.auth.member-group=triplea-game:maintainers
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
  restricted to org `triplea-game`, team `maintainers`; emits identity on the auth subrequest.
- **nginx** (`:8000`, `auth/nginx.conf`) — terminates browser traffic and:
  - **optional-auth** on `/support/maps/status` (anonymous allowed; identity forwarded if present),
  - **hard-gate** on `/support/admin/` (unauthenticated → redirect to login),
  - **header sanitization** everywhere (always overwrites `X-Auth-*`).

> **⚠ Verify the emitted group format on first login.** oauth2-proxy's `X-Auth-Request-Groups`
> value for a GitHub team must match `app.auth.member-group` (`triplea-game:maintainers`). If it
> emits a different form (e.g. just `maintainers`), a real member would pass nginx + oauth2-proxy
> but get 401 from the app's `isMember` check. Align them by setting `app.auth.member-group` (env
> `APP_AUTH_MEMBER_GROUP`) to whatever oauth2-proxy actually emits.

---

## Production deploy

The production reverse proxy (nginx + oauth2-proxy, vault-managed secrets, stable cookie secret,
HTTPS/TLS, prod GitHub OAuth app callback) is managed in the separate **`infrastructure`** repo,
not here. It mirrors `auth/nginx.conf`'s optional-auth + hard-gate + header-sanitization rules.

---

## Key files

| File | Purpose |
|---|---|
| `src/main/java/org/triplea/services/auth/` | Identity, providers, resolver, `@RequiresMember`, filter. |
| `src/main/resources/application.properties` | Header names, member group, `DEV_FAKE_AUTH` wiring. |
| `src/main/resources/templates/MapsStatusController/statusPage.html` | Conditional render + logout link. |
| `docker-compose.auth.yml`, `auth/nginx.conf`, `.env.auth.example` | Local real-proxy overlay. |
| `src/testInteg/java/org/triplea/services/maps/**/Map*AuthIntegrationTest.java` | The three auth states. |
