#!/usr/bin/env bash
#
# End-to-end smoke test against a running URL shortener instance.
#
#   ./mvnw spring-boot:run                                        # terminal A
#   BASE_URL=http://localhost:8080 ./scripts/smoke-test.sh        # terminal B
#
# Covers the core flows plus the main error paths (see docs/TEST_CASES.md).
# Exits non-zero if any check fails. Requires: curl, bash (jq not needed).

set -u

BASE="${BASE_URL:-http://localhost:8080}"
LONG_URL="https://example.com/some/very/long/path?query=1"
PASS=0
FAIL=0

ok()  { printf '  PASS  %s\n' "$1"; PASS=$((PASS + 1)); }
bad() { printf '  FAIL  %s\n         expected: %s\n         actual:   %s\n' "$1" "$2" "$3"; FAIL=$((FAIL + 1)); }
eq()  { if [ "$2" = "$3" ]; then ok "$1"; else bad "$1" "$2" "$3"; fi; }

# --- tiny JSON helpers (our payloads contain no commas inside strings) ---------
jfield() { printf '%s' "$1" | tr ',{}[]' '\n' | tr -d '"' | sed -n "s/^[[:space:]]*$2://p" | head -n 1; }
jlast()  { printf '%s' "$1" | tr ',{}[]' '\n' | tr -d '"' | sed -n "s/^[[:space:]]*$2://p" | tail -n 1; }
jcount() { printf '%s' "$1" | tr ',' '\n' | grep -c "\"$2\""; }

status_of()  { printf '%s' "$1" | tr -d '\r' | head -n 1 | sed -n 's|^HTTP/[0-9.]* \([0-9]\{3\}\).*|\1|p'; }
header_of()  { printf '%s' "$1" | tr -d '\r' | sed -n "s/^$2:[[:space:]]*//Ip" | head -n 1; }

headers() { curl -s -D - -o /dev/null "$@"; }

post_json() { # url json -> sets R_STATUS and R_BODY
    local out
    out=$(curl -s -w '\n%{http_code}' -X POST "$1" -H 'Content-Type: application/json' -d "$2")
    R_STATUS=$(printf '%s\n' "$out" | tail -n 1)
    R_BODY=$(printf '%s\n' "$out" | sed '$d')
}

# UTC timestamp a few seconds in the future (GNU date, then BSD date).
future_expiry() {
    if date -u -d '+4 seconds' >/dev/null 2>&1; then
        date -u -d '+4 seconds' +%Y-%m-%dT%H:%M:%SZ
    else
        date -u -v+4S +%Y-%m-%dT%H:%M:%SZ
    fi
}

printf 'Smoke test against %s\n\n' "$BASE"

# --- TC-035 UI ---------------------------------------------------------------
eq "TC-035 GET / -> 200" 200 "$(status_of "$(headers "$BASE/")")"

# --- TC-001 create -----------------------------------------------------------
post_json "$BASE/api/links" "{\"url\":\"$LONG_URL\"}"
create_status=$R_STATUS
create_body=$R_BODY
code=$(jfield "$create_body" code)

eq "TC-001 POST /api/links -> 201" 201 "$create_status"
eq "TC-036 code is 7 characters" 7 "${#code}"
eq "TC-001 short_url points at this host" "$BASE/$code" "$(jfield "$create_body" short_url)"

# --- TC-002 duplicate URL gets a new code ------------------------------------
post_json "$BASE/api/links" "{\"url\":\"$LONG_URL\"}"
code2=$(jfield "$R_BODY" code)
eq "TC-002 POST again -> 201" 201 "$R_STATUS"
if [ "$code" != "$code2" ]; then ok "TC-002 duplicate URL gets a different code"; else bad "TC-002 duplicate URL gets a different code" "different" "$code == $code2"; fi

# --- TC-018 redirect ---------------------------------------------------------
h=$(headers "$BASE/$code")
eq "TC-018 GET /{code} -> 302" 302 "$(status_of "$h")"
eq "TC-018 Location is the original URL" "$LONG_URL" "$(header_of "$h" 'Location')"

# --- TC-022 details ----------------------------------------------------------
d=$(curl -s "$BASE/api/links/$code")
eq "TC-022 visit_count after 1 redirect" 1 "$(jfield "$d" visit_count)"

# --- TC-025/026 stats window -------------------------------------------------
s=$(curl -s "$BASE/api/links/$code/stats")
eq "TC-025 stats window has 7 days" 7 "$(jcount "$s" date)"
eq "TC-025 today's bucket counted the redirect" 1 "$(jlast "$s" count)"

# --- TC-019 unknown code -----------------------------------------------------
u=$(curl -s -w '\n%{http_code}' "$BASE/zzzzzzz")
eq "TC-019 unknown code -> 404" 404 "$(printf '%s\n' "$u" | tail -n 1)"
eq "TC-019 error code" "NOT_FOUND" "$(jfield "$(printf '%s\n' "$u" | sed '$d')" error)"

# --- TC-008/009 invalid URL --------------------------------------------------
post_json "$BASE/api/links" '{"url":"not-a-url"}'
eq "TC-009 URL without scheme -> 400" 400 "$R_STATUS"
eq "TC-009 error code" "INVALID_URL" "$(jfield "$R_BODY" error)"

post_json "$BASE/api/links" '{"url":"ftp://example.com"}'
eq "TC-010 non-http scheme -> 400" 400 "$R_STATUS"

# --- TC-013 malformed JSON ---------------------------------------------------
post_json "$BASE/api/links" '{not json'
eq "TC-013 malformed body -> 400" 400 "$R_STATUS"
eq "TC-013 error code" "MALFORMED_JSON" "$(jfield "$R_BODY" error)"

# --- TC-003/004 custom alias -------------------------------------------------
post_json "$BASE/api/links" '{"url":"https://example.com/a","code":"myalias"}'
eq "TC-003 custom alias -> 201" 201 "$R_STATUS"
eq "TC-003 alias is honoured" "myalias" "$(jfield "$R_BODY" code)"

post_json "$BASE/api/links" '{"url":"https://example.com/b","code":"myalias"}'
eq "TC-004 duplicate alias -> 409" 409 "$R_STATUS"
eq "TC-004 error code" "CODE_ALREADY_EXISTS" "$(jfield "$R_BODY" error)"

# --- TC-005 invalid alias ----------------------------------------------------
post_json "$BASE/api/links" '{"url":"https://example.com/c","code":"ab"}'
eq "TC-005 too-short alias -> 400" 400 "$R_STATUS"
eq "TC-005 error code" "INVALID_CODE" "$(jfield "$R_BODY" error)"

# --- TC-020 expiry -----------------------------------------------------------
post_json "$BASE/api/links" "{\"url\":\"https://example.com/tmp\",\"code\":\"temp01\",\"expires_at\":\"$(future_expiry)\"}"
eq "TC-020 create with future expiry -> 201" 201 "$R_STATUS"
sleep 5
eq "TC-020 redirect after expiry -> 410" 410 "$(status_of "$(headers "$BASE/temp01")")"

# --- TC-029 concurrency ------------------------------------------------------
if command -v xargs >/dev/null 2>&1; then
    seq 1 50 | xargs -P 20 -I{} curl -s -o /dev/null "$BASE/$code2" >/dev/null 2>&1
    d2=$(curl -s "$BASE/api/links/$code2")
    eq "TC-029 50 concurrent redirects counted exactly" 50 "$(jfield "$d2" visit_count)"
    s2=$(curl -s "$BASE/api/links/$code2/stats")
    eq "TC-029 today's bucket matches" 50 "$(jlast "$s2" count)"
fi

printf '\npassed=%d failed=%d\n' "$PASS" "$FAIL"
[ "$FAIL" -eq 0 ] || exit 1
