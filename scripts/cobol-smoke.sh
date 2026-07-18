#!/usr/bin/env bash
# End-to-end smoke against a running dms-cobol container (dev header
# auth): health, identity, RBAC, org lifecycle, members (iteration 2)
# plus the documents BC — upload/download/metadata/Akten/classes/config
# (iteration 3). Companion to compose-smoke.sh (the as-is Java stack).
set -euo pipefail

BASE="${1:-http://localhost:7861}"
PDF="$(dirname "$0")/smoke.pdf"
ADMIN="admin@example.com"
H_ADMIN=(-H "X-Dev-User: $ADMIN")
H_EDITOR=(-H "X-Dev-User: editor@example.com")
H_NOBODY=(-H "X-Dev-User: nobody@example.com")
JSON=(-H "Content-Type: application/json")

code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

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
CODE=$(code "$BASE/api/v1/orgs")
[ "$CODE" = "401" ] || { echo "::error::expected 401, got $CODE"; exit 1; }

# identity + JIT provisioning; bootstrap admin flag
curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/users/me" | grep -q '"admin":true' \
  || { echo "::error::bootstrap admin not recognized"; exit 1; }
echo "users/me OK"

ORG_ID=$(curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"name":"Smoke"}' "$BASE/api/v1/orgs" | jq -r .id)
echo "org: $ORG_ID"
[ -n "$ORG_ID" ] && [ "$ORG_ID" != "null" ]

# non-root creation by non-admin is forbidden (403)
CODE=$(code -X POST "${H_NOBODY[@]}" "${JSON[@]}" -d '{"name":"Evil"}' "$BASE/api/v1/orgs")
[ "$CODE" = "403" ] || { echo "::error::expected 403, got $CODE"; exit 1; }

# membership: assign, list, ACL visibility
curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"email":"editor@example.com","role":"EDITOR"}' \
  "$BASE/api/v1/orgs/$ORG_ID/members" | grep -q '"role":"EDITOR"'
curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID/members" | grep -q "editor@example.com"
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/orgs" | grep -q "$ORG_ID" \
  || { echo "::error::editor does not see granted org"; exit 1; }
curl -sSf "${H_NOBODY[@]}" "$BASE/api/v1/orgs" | grep -qv "$ORG_ID" \
  || { echo "::error::stranger sees org (ACL broken)"; exit 1; }
echo "members + ACL OK"

# existence-hiding: stranger deleting the org gets 404, not 403
CODE=$(code -X DELETE "${H_NOBODY[@]}" "$BASE/api/v1/orgs/$ORG_ID")
[ "$CODE" = "404" ] || { echo "::error::expected 404, got $CODE"; exit 1; }

# duplicate membership -> 409
CODE=$(code -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"email":"editor@example.com","role":"VIEWER"}' "$BASE/api/v1/orgs/$ORG_ID/members")
[ "$CODE" = "409" ] || { echo "::error::expected 409, got $CODE"; exit 1; }

# rename
curl -sSf -X PUT "${H_ADMIN[@]}" "${JSON[@]}" -d '{"name":"Smoke2"}' \
  "$BASE/api/v1/orgs/$ORG_ID" | grep -q '"name":"Smoke2"'
echo "rename OK"

# ---- config + document classes (iteration 3) -----------------------
curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/config" | grep -q '"documentClasses"' \
  || { echo "::error::/config missing documentClasses"; exit 1; }
# seeded vocabulary is readable by any authenticated user
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/document-classes" | grep -q '"RECHNUNG"'
# only bootstrap admins may create a class
CODE=$(code -X POST "${H_EDITOR[@]}" "${JSON[@]}" \
  -d '{"name":"NEUKLASSE","description":"x"}' "$BASE/api/v1/document-classes")
[ "$CODE" = "403" ] || { echo "::error::class create by editor expected 403, got $CODE"; exit 1; }
curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"name":"NEUKLASSE","description":"x"}' "$BASE/api/v1/document-classes" | grep -q '"NEUKLASSE"'
CODE=$(code -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"name":"neuklasse","description":"y"}' "$BASE/api/v1/document-classes")
[ "$CODE" = "409" ] || { echo "::error::duplicate class expected 409, got $CODE"; exit 1; }
echo "config + classes OK"

# ---- documents BC on a dedicated org -------------------------------
DOCS_ORG=$(curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"name":"DocsOrg"}' "$BASE/api/v1/orgs" | jq -r .id)
curl -sSf -X POST "${H_ADMIN[@]}" "${JSON[@]}" \
  -d '{"email":"editor@example.com","role":"EDITOR"}' \
  "$BASE/api/v1/orgs/$DOCS_ORG/members" >/dev/null

# upload a real PDF (editor) -> 201, RECEIVED, ORIGINAL rendition
DOC_ID=$(curl -sSf -X POST "${H_EDITOR[@]}" \
  -F "file=@$PDF;type=application/pdf;filename=rechnung.pdf" \
  -F "orgUnitId=$DOCS_ORG" "$BASE/api/v1/documents" | jq -r .id)
echo "document: $DOC_ID"
[ -n "$DOC_ID" ] && [ "$DOC_ID" != "null" ]
DOC_JSON=$(curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID")
# the ingest worker may already have advanced the doc past RECEIVED
echo "$DOC_JSON" | grep -qE '"status":"(RECEIVED|CONVERTING|READY)"' \
  || { echo "::error::unexpected initial status"; exit 1; }
echo "$DOC_JSON" | grep -q '"type":"ORIGINAL"' || { echo "::error::no ORIGINAL rendition"; exit 1; }
echo "$DOC_JSON" | grep -q '"producer":"upload"' || { echo "::error::producer not upload"; exit 1; }
EXPECT_SHA=$(sha256sum "$PDF" | cut -d' ' -f1)
echo "$DOC_JSON" | grep -q "$EXPECT_SHA" || { echo "::error::checksum mismatch"; exit 1; }
echo "upload OK"

# ingest worker (§7.5): the queued job is claimed in-process and OCR-only
# conversion drives RECEIVED -> CONVERTING -> READY, producing a TEXT
# rendition (producer ocrmypdf, D-9). No PDF/A rendition is ever created.
echo "waiting for conversion -> READY..."
READY_JSON=""
for _ in $(seq 1 60); do
  READY_JSON=$(curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID")
  echo "$READY_JSON" | grep -q '"status":"FAILED"' && { echo "::error::conversion FAILED"; exit 1; }
  echo "$READY_JSON" | grep -q '"status":"READY"' && break
  sleep 2
done
echo "$READY_JSON" | grep -q '"status":"READY"' || { echo "::error::document did not reach READY"; exit 1; }
echo "$READY_JSON" | grep -q '"type":"TEXT"' || { echo "::error::no TEXT rendition"; exit 1; }
echo "$READY_JSON" | grep -q '"producer":"ocrmypdf"' || { echo "::error::TEXT producer not ocrmypdf"; exit 1; }
echo "$READY_JSON" | grep -q '"type":"PDF_A"' && { echo "::error::unexpected PDF_A rendition (D-9)"; exit 1; } || true
echo "conversion -> READY OK"

# the OCR text is downloadable and carries the document's text layer
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID/file?type=TEXT" -o /tmp/dl.txt
[ -s /tmp/dl.txt ] || { echo "::error::OCR text is empty"; exit 1; }
grep -qi "Smoketest" /tmp/dl.txt || { echo "::error::OCR text missing expected content"; exit 1; }
echo "OCR text OK ($(wc -c </tmp/dl.txt) bytes)"

# /jobs (ACL-scoped) shows this document's job as DONE
JOBS=$(curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/jobs")
echo "$JOBS" | jq -e --arg d "$DOC_ID" 'any(.[]; .documentId==$d and .status=="DONE")' >/dev/null \
  || { echo "::error::/jobs does not show the job DONE"; exit 1; }
# a stranger sees no jobs for this document (visibility follows the doc)
curl -sSf "${H_NOBODY[@]}" "$BASE/api/v1/jobs" | grep -q "$DOC_ID" \
  && { echo "::error::stranger sees job (ACL broken)"; exit 1; } || true
echo "jobs endpoint OK"

# non-PDF -> 415, empty -> 422 (PDF-only intake, D-8)
printf 'hello' > /tmp/notes.txt
CODE=$(code -X POST "${H_EDITOR[@]}" -F "file=@/tmp/notes.txt;type=text/plain" \
  -F "orgUnitId=$DOCS_ORG" "$BASE/api/v1/documents")
[ "$CODE" = "415" ] || { echo "::error::non-PDF expected 415, got $CODE"; exit 1; }
: > /tmp/empty.pdf
CODE=$(code -X POST "${H_EDITOR[@]}" -F "file=@/tmp/empty.pdf;type=application/pdf;filename=e.pdf" \
  -F "orgUnitId=$DOCS_ORG" "$BASE/api/v1/documents")
[ "$CODE" = "422" ] || { echo "::error::empty file expected 422, got $CODE"; exit 1; }
echo "upload validation OK"

# download round-trip: byte-for-byte identical to the source PDF
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID/file?type=ORIGINAL" -o /tmp/dl.pdf
cmp "$PDF" /tmp/dl.pdf || { echo "::error::download differs from source"; exit 1; }
echo "download OK"

# ACL: stranger cannot see the document (404) or the list
CODE=$(code "${H_NOBODY[@]}" "$BASE/api/v1/documents/$DOC_ID")
[ "$CODE" = "404" ] || { echo "::error::stranger doc read expected 404, got $CODE"; exit 1; }
curl -sSf "${H_NOBODY[@]}" "$BASE/api/v1/documents" | grep -q "$DOC_ID" \
  && { echo "::error::stranger sees document (ACL broken)"; exit 1; } || true

# metadata: 404 before save, validation, then save (Aktenbildung)
CODE=$(code "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID/metadata")
[ "$CODE" = "404" ] || { echo "::error::metadata pre-save expected 404, got $CODE"; exit 1; }
CODE=$(code -X PUT "${H_EDITOR[@]}" "${JSON[@]}" \
  -d '{"documentDate":"2026-13-40","documentClass":"RECHNUNG","filePlanReference":"KD-1"}' \
  "$BASE/api/v1/documents/$DOC_ID/metadata")
[ "$CODE" = "422" ] || { echo "::error::bad date expected 422, got $CODE"; exit 1; }
CODE=$(code -X PUT "${H_EDITOR[@]}" "${JSON[@]}" \
  -d '{"documentDate":"2026-07-18","documentClass":"NOPE","filePlanReference":"KD-1"}' \
  "$BASE/api/v1/documents/$DOC_ID/metadata")
[ "$CODE" = "422" ] || { echo "::error::bad class expected 422, got $CODE"; exit 1; }
META=$(curl -sSf -X PUT "${H_EDITOR[@]}" "${JSON[@]}" \
  -d '{"documentDate":"2026-07-18","documentClass":"rechnung","filePlanReference":" KD-4711 "}' \
  "$BASE/api/v1/documents/$DOC_ID/metadata")
echo "$META" | grep -q '"documentClass":"RECHNUNG"' || { echo "::error::class not normalized"; exit 1; }
echo "$META" | grep -q '"filePlanReference":"KD-4711"' || { echo "::error::fpr not trimmed"; exit 1; }
echo "$META" | grep -q '"version":1' || { echo "::error::version not 1"; exit 1; }
AKTE_ID=$(echo "$META" | jq -r .akteId)
[ -n "$AKTE_ID" ] && [ "$AKTE_ID" != "null" ] || { echo "::error::Aktenbildung did not link an Akte"; exit 1; }
echo "metadata + Aktenbildung OK"

# Akten: list + documents in the akte (ordered, ACL-filtered)
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/akten" | grep -q "$AKTE_ID"
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/akten/$AKTE_ID/documents" | grep -q "$DOC_ID" \
  || { echo "::error::akte documents missing the document"; exit 1; }
echo "akten OK"

# reprocess -> 200; the job is re-queued and the worker re-runs the
# idempotent OCR conversion, landing the document back at READY. The
# synchronous response reflects the pipeline restart (the worker may
# already have re-claimed it by the time we re-read).
curl -sSf -X POST "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID/reprocess" \
  | grep -qE '"status":"(RECEIVED|CONVERTING|READY)"' \
  || { echo "::error::reprocess did not restart the pipeline"; exit 1; }
for _ in $(seq 1 60); do
  curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID" \
    | grep -q '"status":"READY"' && break
  sleep 2
done
curl -sSf "${H_EDITOR[@]}" "$BASE/api/v1/documents/$DOC_ID" \
  | grep -q '"status":"READY"' || { echo "::error::reprocess did not reach READY"; exit 1; }
echo "reprocess OK"

# ---- teardown: the empty org deletes cleanly (204) -----------------
MEMBERSHIP_ID=$(curl -sSf "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID/members" \
  | jq -r '.[0].membershipId')
CODE=$(code -X DELETE "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID/members/$MEMBERSHIP_ID")
[ "$CODE" = "204" ] || { echo "::error::member delete expected 204, got $CODE"; exit 1; }
CODE=$(code -X DELETE "${H_ADMIN[@]}" "$BASE/api/v1/orgs/$ORG_ID")
[ "$CODE" = "204" ] || { echo "::error::org delete expected 204, got $CODE"; exit 1; }
echo "teardown OK"

echo "cobol smoke OK"
