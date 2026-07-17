package de.dms.documents.control;

import java.util.List;
import java.util.Map;

/**
 * AI-suggested metadata; every value is optional (graceful degradation).
 * The well-known fields feed the metadata suggestions; {@code additional}
 * carries any extra configured extraction fields, which are folded into the
 * search index text. The document's full text is not part of the suggestions:
 * the search index uses the deterministic OCR text from the conversion
 * service instead of a model echo.
 *
 * <p>{@code ordnungsbegriffe} is three-valued: a non-empty list holds the
 * extracted business reference identifiers, an empty list means the model
 * found none (document needs manual indexing), {@code null} means the
 * section of the answer was malformed (document needs review) — the other
 * fields still apply in that case.
 */
public record MetadataSuggestions(
        String documentDate,
        String documentClass,
        String filePlanReference,
        Map<String, String> additional,
        List<Ordnungsbegriff> ordnungsbegriffe) {

    /** One extracted Ordnungsbegriff: the configured type name and the value found. */
    public record Ordnungsbegriff(String type, String value) {
    }
}
