-- Extraction catalogs: the controlled vocabulary (document classes) and the
-- AI extraction intents move from static configuration into the database so
-- both can be managed at runtime; the AI system prompt is assembled from
-- these tables on every extraction. Classes and intents are independent
-- catalogs — the model picks one of each.

CREATE TABLE document_class (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,   -- upper-case code, e.g. RECHNUNG
    description TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);

CREATE TABLE extraction_intent (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL UNIQUE,
    description TEXT NOT NULL,
    created_at  INTEGER NOT NULL
);

-- name is the JSON key the model answers with; reserved keys
-- (documentDate, documentClass, filePlanReference, intent, extractedText)
-- are rejected at the service layer.
CREATE TABLE extraction_intent_field (
    id          TEXT PRIMARY KEY,
    intent_id   TEXT NOT NULL REFERENCES extraction_intent(id),
    name        TEXT NOT NULL,
    description TEXT NOT NULL,
    UNIQUE (intent_id, name)
);
CREATE INDEX idx_intent_field_intent ON extraction_intent_field(intent_id);

-- Seed: the previous config defaults (DMS_DOCUMENT_CLASSES) as rows.
INSERT INTO document_class (id, name, description, created_at) VALUES
    ('dc-rechnung',  'RECHNUNG',  'Rechnungen, Gutschriften und Zahlungsaufforderungen', strftime('%s','now') * 1000),
    ('dc-vertrag',   'VERTRAG',   'Verträge, Vertragsänderungen und Kündigungen', strftime('%s','now') * 1000),
    ('dc-antrag',    'ANTRAG',    'Anträge und Antragsformulare von Bürgern oder Mitarbeitern', strftime('%s','now') * 1000),
    ('dc-bescheid',  'BESCHEID',  'Behördliche Bescheide und förmliche Entscheidungen', strftime('%s','now') * 1000),
    ('dc-bericht',   'BERICHT',   'Berichte, Protokolle und Gutachten', strftime('%s','now') * 1000),
    ('dc-sonstiges', 'SONSTIGES', 'Alle Dokumente, die in keine andere Klasse passen', strftime('%s','now') * 1000);

-- One example intent so the feature is demonstrable out of the box.
INSERT INTO extraction_intent (id, name, description, created_at) VALUES
    ('in-rechnungseingang', 'Rechnungseingang',
     'Eine eingehende Rechnung, die geprüft und zur Zahlung angewiesen werden soll', strftime('%s','now') * 1000);

INSERT INTO extraction_intent_field (id, intent_id, name, description) VALUES
    ('inf-absender',    'in-rechnungseingang', 'absender',          'Name des Rechnungsstellers (Absender)'),
    ('inf-rechnungsnr', 'in-rechnungseingang', 'rechnungsnummer',   'Die Rechnungsnummer'),
    ('inf-betrag',      'in-rechnungseingang', 'betrag',            'Der Bruttobetrag inklusive Währung, z. B. 119,00 EUR'),
    ('inf-faellig',     'in-rechnungseingang', 'faelligkeitsdatum', 'Das Fälligkeitsdatum der Zahlung, ISO-Format yyyy-MM-dd');
