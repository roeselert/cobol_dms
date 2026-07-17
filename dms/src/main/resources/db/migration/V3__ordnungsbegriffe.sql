-- Ordnungsbegriff types: the admin-managed catalog of business reference
-- identifiers (Kundennummer, Vertragsnummer, ...) the AI extraction looks
-- for; name and description of every active type feed the system prompt.
-- Extracted values land in document_ordnungsbegriff with the type NAME as a
-- denormalized snapshot — deliberately no FK — so deleting or deactivating a
-- type never touches metadata already stored on processed documents.

CREATE TABLE ordnungsbegriff_type (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,   -- human label, e.g. Kundennummer
    description TEXT NOT NULL,
    active      INTEGER NOT NULL DEFAULT 1,
    created_at  INTEGER NOT NULL
);

CREATE TABLE document_ordnungsbegriff (
    id              TEXT PRIMARY KEY,
    document_id     TEXT NOT NULL REFERENCES document(id),
    type_name       TEXT NOT NULL,      -- snapshot of ordnungsbegriff_type.name, no FK
    value           TEXT NOT NULL,
    extracted_by_ai INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL,
    UNIQUE (document_id, type_name, value)
);
CREATE INDEX idx_doc_ordnungsbegriff_doc ON document_ordnungsbegriff(document_id);

-- MANUAL_INDEXING (no Ordnungsbegriff found / AI unconfigured) or REVIEW
-- (extraction errored); set by the ingest pipeline, cleared on user-confirmed
-- metadata save.
ALTER TABLE document_metadata ADD COLUMN indexing_flag TEXT;

-- Seed: two example types so the feature is demonstrable out of the box.
INSERT INTO ordnungsbegriff_type (id, name, description, active, created_at) VALUES
    ('ob-kundennummer', 'Kundennummer',
     'Die Kundennummer des Absenders oder Empfängers', 1, strftime('%s','now') * 1000),
    ('ob-vertragsnummer', 'Vertragsnummer',
     'Die Vertrags- oder Policennummer, auf die sich das Dokument bezieht', 1, strftime('%s','now') * 1000);
