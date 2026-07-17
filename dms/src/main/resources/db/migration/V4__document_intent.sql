-- The detected intent per document: the extraction model picks the single
-- best-matching intent and extracts that intent's fields. One row per
-- document; a reprocess replaces the row. The field values are stored as a
-- JSON object string ({"fieldName":"value", ...}) — display-only data, never
-- queried by field.
CREATE TABLE document_intent (
    document_id TEXT PRIMARY KEY REFERENCES document(id),
    name        TEXT NOT NULL,
    fields_json TEXT,
    created_at  INTEGER NOT NULL
);
