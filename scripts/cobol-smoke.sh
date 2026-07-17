#!/usr/bin/env bash
# End-to-end smoke against a running dms-cobol container (dev header
# auth): health, identity, RBAC, org lifecycle, members — the whole
# iteration-2 organization slice. Companion to compose-smoke.sh (which
# smokes the as-is Java stack).
set -euo pipefail

BASE="${1:-http://localhost:7861}"
ADMIN="admin@example.com"
H_ADMIN=(-H "X-Dev-User: $ADMIN")
H_EDITOR=(-H "X-Dev-User: editor@example.com")
H_NOBODY=(-H "X-Dev-User: nobody@example.com")
JSON=(-H "Content-Type: application/json")

echo "waiting for $BASE ..."
for _ in $(seq 1 120); do
  if curl -sSf "$BASE/api/v1/health" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -sSf "$BASE/api/v1/health" | grep -q '"status":"ok"'
echo "health OK"

# static frontend is served by Apache
curl -sSf "$BASE/" | grep -qi "<html" || { echo "::error::frontend not served"; exit 1; }
echo "frontend OK"

# unauthenticated -> 401
CODE=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/v1/orgs")
[ "$CODE" = "401" ] || { echo "::error::expected 401, got $CODE"; exit 1; }

# identity + JIT provisioning; bootstrap admin flag
curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/users/me" | grep -q '"admin":true' \
  || { echo "::error::bootstrap admin not recognized"; exit 1; }
echo "users/me OK"

ORG_ID=$(curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"name":"Smoke"}' "$BASE/api/v1/orgs" | jq -r .id)
echo "org: $ORG_ID"
[ -n "$ORG_ID" ] && [ "$ORG_ID" != "null" ]

# non-root creation by non-admin is forbidden (403), root hidden (404)
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${H_NOBODY[@]}" "${JSON[@]}" \
  -d '{"name":"Evil"}' "$BASE/api/v1/orgs")
[ "$CODE" = "403" ] || { echo "::error::expected 403, got $CODE"; exit 1; }

# membership: assign, list, ACL visibility
curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"email":"editor@example.com","role":"EDITOR"}' \
  "$BASE/api/v1/orgs/$ORG_ID/members" | grep -q '"role":"EDITOR"'
curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID/members" \
  | grep -q "editor@example.com"
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/orgs" | grep -q "$ORG_ID" \
  || { echo "::error::editor does not see granted org"; exit 1; }
curl -sSf "${H_NOBODY[@]}" "$BASE/api/v1/orgs" | grep -qv "$ORG_ID" \
  || { echo "::error::stranger sees org (ACL broken)"; exit 1; }
echo "members + ACL OK"

# existence-hiding: stranger deleting the org gets 404, not 403
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "${H_NOBODY[@]}" \
  "$BASE/api/v1/orgs/$ORG_ID")
[ "$CODE" = "404" ] || { echo "::error::expected 404, got $CODE"; exit 1; }

# duplicate membership -> 409
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"email":"editor@example.com","role":"VIEWER"}' \
  "$BASE/api/v1/orgs/$ORG_ID/members")
[ "$CODE" = "409" ] || { echo "::error::expected 409, got $CODE"; exit 1; }

# rename
curl -sSf -X PUT "${H_ADMIN[@]}" "${JSON[@]}" -d '{"name":"Smoke2"}' \
  "$BASE/api/v1/orgs/$ORG_ID" | grep -q '"name":"Smoke2"'
echo "rename OK"

# teardown: revoke membership, delete org (guards allow it now)
MEMBERSHIP_ID=$(curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID/members" \
  | jq -r '.[0].membershipId')
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "${H_ADMIN[@]}" \
  "$BASE/api/v1/orgs/$ORG_ID/members/$MEMBERSHIP_ID")
[ "$CODE" = "204" ] || { echo "::error::expected 204, got $CODE"; exit 1; }
CODE=$(curl -s -o /dev/null -w '%{http_code}' -X DELETE "${H_ADMIN[@]}" \
  "$BASE/api/v1/orgs/$ORG_ID")
[ "$CODE" = "204" ] || { echo "::error::expected 204, got $CODE"; exit 1; }
echo "teardown OK"

echo "cobol smoke OK"
