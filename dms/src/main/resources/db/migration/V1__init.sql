-- Cloud DMS iteration-1 schema (SQLite, WAL mode).
-- Timestamps are stored as epoch milliseconds (INTEGER) so native queries
-- (job claim, lease sweep, feed) can compare them without dialect quirks.
-- document_date is an ISO-8601 date string (yyyy-MM-dd).

CREATE TABLE org_unit (
    id        TEXT PRIMARY KEY,
    name      TEXT NOT NULL,
    parent_id TEXT REFERENCES org_unit(id),
    path      TEXT NOT NULL UNIQUE
);
CREATE INDEX idx_org_unit_parent ON org_unit(parent_id);

CREATE TABLE dms_user (
    id           TEXT PRIMARY KEY,
    email        TEXT NOT NULL UNIQUE,
    display_name TEXT,
    status       TEXT NOT NULL          -- INVITED | ACTIVE | DISABLED
);

CREATE TABLE membership (
    id          TEXT PRIMARY KEY,
    user_id     TEXT NOT NULL REFERENCES dms_user(id),
    org_unit_id TEXT NOT NULL REFERENCES org_unit(id),
    role        TEXT NOT NULL,          -- ADMIN | EDITOR | VIEWER
    UNIQUE (user_id, org_unit_id)
);
CREATE INDEX idx_membership_user ON membership(user_id);
CREATE INDEX idx_membership_org  ON membership(org_unit_id);

CREATE TABLE document (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    uploaded_by TEXT NOT NULL REFERENCES dms_user(id),
    org_unit_id TEXT NOT NULL REFERENCES org_unit(id),
    ingest_date INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);
CREATE INDEX idx_document_org    ON document(org_unit_id);
CREATE INDEX idx_document_ingest ON document(ingest_date);

CREATE TABLE document_status (
    document_id TEXT PRIMARY KEY REFERENCES document(id),
    status      TEXT NOT NULL,          -- RECEIVED | CONVERTING | READY | FAILED
    changed_by  TEXT,
    updated_at  INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);

CREATE TABLE rendition (
    id              TEXT PRIMARY KEY,
    document_id     TEXT NOT NULL REFERENCES document(id),
    type            TEXT NOT NULL,      -- ORIGINAL | PDF_A
    storage_key     TEXT NOT NULL,
    mime_type       TEXT NOT NULL,
    size_bytes      INTEGER NOT NULL,
    checksum_sha256 TEXT NOT NULL,
    created_at      INTEGER NOT NULL,
    UNIQUE (document_id, type)
);

CREATE TABLE akte (
    id                  TEXT PRIMARY KEY,
    file_plan_reference TEXT NOT NULL UNIQUE,
    org_unit_id         TEXT NOT NULL REFERENCES org_unit(id),
    created_at          INTEGER NOT NULL
);

CREATE TABLE document_metadata (
    document_id     TEXT PRIMARY KEY REFERENCES document(id),
    document_date   TEXT,
    document_class  TEXT,
    extracted_by_ai INTEGER NOT NULL DEFAULT 0,
    updated_by      TEXT,
    version         INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL,
    updated_at      INTEGER NOT NULL
);

CREATE TABLE document_file_plan_reference (
    document_id         TEXT PRIMARY KEY REFERENCES document(id),
    file_plan_reference TEXT NOT NULL,
    akte_id             TEXT REFERENCES akte(id),
    extracted_by_ai     INTEGER NOT NULL DEFAULT 0,
    updated_by          TEXT,
    version             INTEGER NOT NULL DEFAULT 0,
    created_at          INTEGER NOT NULL
);
CREATE INDEX idx_fpr_akte ON document_file_plan_reference(akte_id);

CREATE TABLE conversion_job (
    id           TEXT PRIMARY KEY,
    document_id  TEXT NOT NULL REFERENCES document(id),
    status       TEXT NOT NULL,         -- QUEUED | RUNNING | DONE | FAILED
    attempts     INTEGER NOT NULL DEFAULT 0,
    available_at INTEGER NOT NULL,
    lease_until  INTEGER,
    last_error   TEXT,
    created_at   INTEGER NOT NULL
);
CREATE INDEX idx_job_claim ON conversion_job(status, available_at);

CREATE TABLE feed_token (
    id         TEXT PRIMARY KEY,
    user_id    TEXT NOT NULL REFERENCES dms_user(id),
    token_hash TEXT NOT NULL UNIQUE,
    created_at INTEGER NOT NULL,
    revoked_at INTEGER
);

-- user_id is deliberately NOT a foreign key: audit rows must survive user deletion
CREATE TABLE audit_log_entry (
    id            TEXT PRIMARY KEY,
    user_id       TEXT,
    action        TEXT NOT NULL,        -- READ | WRITE | DELETE
    resource_type TEXT NOT NULL,
    resource_id   TEXT,
    effect        TEXT NOT NULL,        -- ALLOW | DENY
    timestamp     INTEGER NOT NULL,
    source_ip     TEXT
);
CREATE INDEX idx_audit_ts ON audit_log_entry(timestamp);

-- FTS5 search projection; org_unit_id is stored (UNINDEXED) so the ACL
-- predicate can be pushed into the search query itself (S-1).
CREATE VIRTUAL TABLE document_fts USING fts5(
    document_id UNINDEXED,
    org_unit_id UNINDEXED,
    name,
    document_class,
    file_plan_reference,
    content_text
);
