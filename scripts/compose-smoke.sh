#!/usr/bin/env bash
# End-to-end smoke against a running docker-compose stack (dev header auth):
# create org + membership, upload a real PDF, wait for READY, assert the
# PDF/A and TEXT renditions and a full-text hit on a word from the PDF body —
# proof that the conversion service's text reached the search index.
set -euo pipefail

BASE="${1:-http://localhost:7860}"
ADMIN="admin@example.com"
PDF="$(dirname "$0")/smoke.pdf"

echo "waiting for $BASE ..."
for _ in $(seq 1 120); do
  if curl -sSf "$BASE/actuator/health/liveness" >/dev/null 2>&1; then break; fi
  sleep 1
done
curl -sSf "$BASE/actuator/health/liveness" >/dev/null

ORG_ID=$(curl -sSf -X POST "$BASE/api/v1/orgs" \
  -H "X-Dev-User: $ADMIN" -H "Content-Type: application/json" \
  -d '{"name":"Smoke"}' | jq -r .id)
echo "org: $ORG_ID"

curl -sSf -X POST "$BASE/api/v1/orgs/$ORG_ID/members" \
  -H "X-Dev-User: $ADMIN" -H "Content-Type: application/json" \
  -d "{\"email\":\"$ADMIN\",\"role\":\"EDITOR\"}" >/dev/null

DOC_ID=$(curl -sSf -X POST "$BASE/api/v1/documents" \
  -H "X-Dev-User: $ADMIN" \
  -F "file=@$PDF;type=application/pdf;filename=rechnung.pdf" \
  -F "orgUnitId=$ORG_ID" | jq -r .id)
echo "document: $DOC_ID"

STATUS=""
for _ in $(seq 1 120); do
  STATUS=$(curl -sSf "$BASE/api/v1/documents/$DOC_ID" -H "X-Dev-User: $ADMIN" | jq -r .status)
  if [ "$STATUS" = "READY" ] || [ "$STATUS" = "FAILED" ]; then break; fi
  sleep 1
done
echo "status: $STATUS"
[ "$STATUS" = "READY" ] || { echo "::error::document never became READY"; exit 1; }

RENDITIONS=$(curl -sSf "$BASE/api/v1/documents/$DOC_ID" -H "X-Dev-User: $ADMIN" | jq -r '.renditions[].type')
echo "renditions: $RENDITIONS"
echo "$RENDITIONS" | grep -q "PDF_A" || { echo "::error::no PDF_A rendition"; exit 1; }
echo "$RENDITIONS" | grep -q "TEXT" || { echo "::error::no TEXT rendition"; exit 1; }

# the word exists only in the PDF body — a hit proves OCR text reached the index
HITS=$(curl -sSf "$BASE/api/v1/search?q=Smoketest" -H "X-Dev-User: $ADMIN" | jq length)
echo "search hits for 'Smoketest': $HITS"
[ "$HITS" -ge 1 ] || { echo "::error::conversion text did not reach the search index"; exit 1; }

echo "smoke OK"
