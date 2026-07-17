# CLAUDE.md — Cloud DMS → GNU COBOL Migration

## What this project is

This repository contains a **document management system (DMS)** taken over from another
team. It is currently implemented as:

- `dms/` — Spring Boot 3 / Java 21 backend (BCE architecture, SQLite, S3/filesystem
  object store, in-process job worker) plus a vanilla-JS single-page frontend
  (`dms/src/main/resources/static/`)
- `services/conversion/` — Python FastAPI service: PDF/A normalization + OCR + text
  extraction (ocrmypdf, ghostscript, libreoffice, pdftotext)
- `services/extraction/` — Python FastAPI service: AI metadata extraction against an
  OpenAI-compatible chat-completions API

**Our organization has no Java know-how.** The mission is to migrate the entire backend
(Java **and** Python) to **GNU COBOL (GnuCOBOL)** while preserving the externally
observable behavior: the same REST API (`/api/v1/...`), the same domain rules, the same
ingest pipeline semantics.

The reverse-engineered as-is architecture is documented in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md); the target COBOL architecture in
[`docs/TARGET-ARCHITECTURE.md`](docs/TARGET-ARCHITECTURE.md). Keep both documents
up to date as the migration proceeds.

## Migration fundamentals (binding rules)

1. **Convert all Java code to GNU COBOL.** No Java remains at the end; behavior parity
   over structural parity, but keep the BCE business-component cut (documents, search,
   conversion, aiextraction, feeds, organization, crosscutting).
2. **Keep the JavaScript frontend.** Re-skin it as a **classic green screen**
   (phosphor-green/amber on black, monospace, block cursor feel) while staying
   **mobile-first** responsive. The views, navigation and REST calls stay.
3. **Apache httpd + CGI serves the REST API.** A **generic HTTP/REST handling layer**
   (CGI env parsing, routing on `PATH_INFO`, JSON parse/emit, status/header emission,
   error mapping) is strictly **separate from business logic**. Business boundary
   programs never touch CGI variables directly.
4. **Migrate the Python services to GNU COBOL too**, and integrate them **in-process
   (direct CALL), not as HTTP services**. The conversion toolchain keeps invoking the
   same external CLI tools (ocrmypdf, gs, libreoffice, pdftotext); the extraction logic
   (prompt assembly, response parsing) becomes COBOL.
5. **VSAM-style storage, no SQLite.** All tables move to GnuCOBOL
   `ORGANIZATION IS INDEXED` files (VSAM KSDS equivalent) with primary and alternate
   record keys. No SQL anywhere. SQLite FTS5 has no VSAM equivalent — full-text search
   is reimplemented on an own token-index file (see target doc).
6. **LLM calls via libcurl as a C library.** COBOL `CALL`s into libcurl
   (`curl_easy_init`/`curl_easy_setopt`/`curl_easy_perform`) for the
   OpenAI-compatible chat-completions request. No shelling out to the curl binary.
7. **One flat source directory** (`src/`) for all COBOL sources and copybooks.
   **File names are max 12 characters** including extension. The naming syntax below
   encodes business-component ordering.
8. **Iteration 1 is documentation only** — no code changes. Migration code starts in
   later iterations.

## Source file naming convention

All COBOL lives flat in `src/`. Name pattern (8 chars + 4-char extension = 12):

```
NNMMMMLS.cob     programs        NNMMMMLS.cpy     copybooks
││└┬─┘│└─ sequence digit 0–9
││ │  └── layer:  B boundary · C control · E entity/file access · W worker/daemon · R record layout (copybooks)
││ └───── 4-char module mnemonic
└┴─────── 2-digit business-component ordinal (sort order = build/read order)
```

Business-component ordinals:

| NN | Component        | Migrates from                                            |
|----|------------------|----------------------------------------------------------|
| 00 | platform         | crosscutting.platform (HTTP/CGI, JSON, config, errors, paging) |
| 10 | security         | crosscutting.security + crosscutting.accesscontrol (authz, audit) |
| 20 | organization     | organization (org units, users, memberships)             |
| 30 | documents        | documents (ingest, metadata, Akten, classes, config)     |
| 40 | search           | documents.search (indexer + query)                       |
| 50 | conversion       | conversion BC + Python conversion service (jobs, worker, PDF/A+OCR) |
| 60 | aiextraction     | aiextraction BC + Python extraction service (catalogs, LLM via libcurl) |
| 70 | feeds            | feeds (RSS inbox feed, feed tokens)                      |
| 90 | operations       | backup, bootstrap/seeding                                |

Examples: `00HTTPC0.cob` (CGI/HTTP layer), `00JSONC0.cob` (JSON parse/emit),
`30DOCSB0.cob` (documents REST boundary), `30DOCSE0.cob` (document file access),
`30DOCSR0.cpy` (document record layout), `50JOBSW0.cob` (ingest worker daemon),
`60CURLC0.cob` (libcurl wrapper), `60EXTRC0.cob` (extraction control).

## Target runtime at a glance

- **Apache httpd**: serves the static green-screen frontend, terminates auth
  (OIDC via `mod_auth_openidc` in production, trusted dev header in dev — mirroring
  today's `oidc`/`dev` security modes), and runs one CGI dispatcher per request.
- **CGI dispatcher** (`00HTTPC0`): routes `/api/v1/...` to boundary programs.
- **Worker daemon** (`50JOBSW0`): long-running GnuCOBOL process polling the VSAM job
  queue file — replaces the Spring scheduler; same claim/lease/retry/backoff semantics.
- **Data**: VSAM indexed files under a data directory; original/PDF-A/text binaries in
  the filesystem object store (S3 support dropped or deferred — open decision).
- **LLM**: libcurl from COBOL, provider config via environment
  (`DMS_AI_URL`/`DMS_AI_TOKEN`/`DMS_AI_MODEL`), JSON-object response contract unchanged.

## Domain language (keep the German terms)

`Akte` (case file, keyed by `filePlanReference` / Aktenzeichen), `Aktenbildung`
(automatic case-file formation), `Ordnungsbegriff` (business reference identifier,
e.g. Kundennummer), document classes (RECHNUNG, VERTRAG, ANTRAG, BESCHEID, BERICHT,
SONSTIGES), extraction intents with fields. Do not translate these in code or UI.

## Invariants to preserve (behavior parity checklist)

- Binary is durably stored **before** any metadata is written; storage down ⇒ 503, no orphans.
- Ingest pipeline: upload → RECEIVED → queue job → CONVERTING → PDF/A + OCR text →
  optional AI suggestions → index → READY; terminal FAILED after max attempts;
  lease-based re-queue of crashed jobs; reprocess is idempotent (deterministic storage keys).
- AI extraction is optional and degrades gracefully: unconfigured ⇒ `MANUAL_INDEXING`
  flag, errored ⇒ `REVIEW` flag — the document still becomes READY.
- Authorization: roles ADMIN/EDITOR/VIEWER on org units, inherited down the org-unit
  hierarchy (path-based); search results are ACL-filtered in the query itself.
- Audit log survives user deletion (no FK); every access decision is auditable.
- Feed tokens are stored hashed; RSS feed authenticates via token query parameter.
- API error contract, status codes (400/403/404/409/413/415/422/502/503/504) and
  `/api/v1` paths stay exactly as documented in `docs/ARCHITECTURE.md` §10.

## Working agreements

- Documentation lives in `docs/`; diagrams are Mermaid (see `.claude/skills` conventions).
- Update `docs/TARGET-ARCHITECTURE.md` open-decision list when a decision falls.
- Development branch: `claude/java-cobol-migration-plan-r7js07`.
