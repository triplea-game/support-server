#!/usr/bin/env bash
#
# End-to-end verification that the reverse proxy sanitizes inbound X-Auth-* headers, so a browser
# cannot spoof identity by sending its own (the security crux described in docs/auth.md).
#
# It proves the property by contrast against two endpoints:
#
#   1. App directly (:8080) WITH a spoofed X-Auth-Email  -> the app trusts it (appears signed in).
#      This is expected: the app is designed to trust its proxy. It is the reason the proxy MUST
#      strip the header — and confirms the spoof header is actually wired through when nothing
#      sanitizes it, so step 2 is a meaningful test rather than a no-op.
#   2. nginx (:8000) WITH the same spoofed header        -> stripped; the app sees anonymous.
#
# Plus a hard-gate check: a spoofed header on /support/admin must NOT yield a 200 (it redirects to
# login instead).
#
# Requires the auth overlay running (nginx :8000 + oauth2-proxy + host quarkusDev :8080) — start it
# with `make run`, or run this via `make verify-auth-headers` which brings the proxy up first.
#
# This is the "what we can do now" version: the app still runs on the host via quarkusDev. The
# longer-term direction is to containerize the app too and drive the whole stack from docker compose
# so this becomes a fully self-contained, CI-runnable integration test.

set -euo pipefail

NGINX_URL="${NGINX_URL:-http://localhost:8000}"
APP_URL="${APP_URL:-http://localhost:8080}"
STATUS_PATH="/support/maps/status"
ADMIN_PATH="/support/admin/map/attributes"

SPOOFED_EMAIL="attacker@evil.example"
SPOOFED_GROUPS="triplea-game:maintainers"

pass=0
fail=0

green() { printf '\033[32m%s\033[0m\n' "$1"; }
red()   { printf '\033[31m%s\033[0m\n' "$1"; }

ok()   { green "PASS  $1"; pass=$((pass + 1)); }
bad()  { red   "FAIL  $1"; fail=$((fail + 1)); }

# --- preflight: both tiers must be reachable -----------------------------------------------------
preflight() {
  if ! curl -sf -o /dev/null "$APP_URL$STATUS_PATH"; then
    red "App not reachable at $APP_URL — start it with 'make run' (or 'make dev')."
    exit 2
  fi
  if ! curl -sf -o /dev/null "$NGINX_URL$STATUS_PATH"; then
    red "nginx not reachable at $NGINX_URL — start the proxy ('make run' or 'make verify-auth-headers')."
    exit 2
  fi
}

# --- the checks ----------------------------------------------------------------------------------
main() {
  preflight

  # 1. Sanity: the app DOES honor X-Auth-Email when nothing strips it. If this fails, the spoof
  #    header isn't reaching the app and step 2 would pass vacuously.
  local direct
  direct=$(curl -s -H "X-Auth-Email: $SPOOFED_EMAIL" -H "X-Auth-Groups: $SPOOFED_GROUPS" \
    "$APP_URL$STATUS_PATH")
  if grep -qF "$SPOOFED_EMAIL" <<<"$direct"; then
    ok "app (:8080) trusts a spoofed X-Auth-Email (expected — proves the header is wired through)"
  else
    bad "app (:8080) did not reflect the spoofed email; the test below would be vacuous"
  fi

  # 2. The real check: nginx must strip the spoofed header so the app sees anonymous.
  local viaProxy
  viaProxy=$(curl -s -H "X-Auth-Email: $SPOOFED_EMAIL" -H "X-Auth-Groups: $SPOOFED_GROUPS" \
    "$NGINX_URL$STATUS_PATH")
  if grep -qF "$SPOOFED_EMAIL" <<<"$viaProxy"; then
    bad "nginx FORWARDED the spoofed X-Auth-Email — header sanitization is BROKEN"
  elif grep -qF "Read-only view" <<<"$viaProxy"; then
    ok "nginx (:8000) stripped the spoofed header — app rendered the anonymous read-only view"
  else
    bad "nginx response neither showed the spoofed email nor the anonymous view (unexpected output)"
  fi

  # 3. Hard-gate: a spoofed header on /support/admin must not grant access (expect a login redirect,
  #    not 200). oauth2-proxy/nginx redirect unauthenticated callers to sign-in.
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    -H "X-Auth-Email: $SPOOFED_EMAIL" -H "X-Auth-Groups: $SPOOFED_GROUPS" \
    "$NGINX_URL$ADMIN_PATH")
  if [[ "$code" == "200" ]]; then
    bad "spoofed header reached the gated admin page (HTTP 200) — hard-gate BROKEN"
  else
    ok "spoofed header on admin page did not grant access (HTTP $code — redirected to login)"
  fi

  echo
  if [[ "$fail" -gt 0 ]]; then
    red "Header sanitization check FAILED: $pass passed, $fail failed."
    exit 1
  fi
  green "Header sanitization check passed ($pass checks)."
}

main "$@"
