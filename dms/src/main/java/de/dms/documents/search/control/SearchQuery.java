package de.dms.documents.search.control;

import de.dms.crosscutting.platform.control.SqlJson;
import de.dms.documents.search.entity.IndexedDocumentRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Translates query + filters into the FTS5 repository query with ACL
 * push-down: the caller's visible-org-unit predicate is part of the SQL,
 * never applied post-hoc, so a forbidden hit is never produced and cannot
 * leak via result counts or paging (S-1).
 */
@Service
public class SearchQuery {

    private final IndexedDocumentRepository index;

    public SearchQuery(IndexedDocumentRepository index) {
        this.index = index;
    }

    public record SearchHit(String documentId, String name, String orgUnitId, String snippet) {
    }

    public record Filters(String documentClass, String filePlanReference, String dateFrom, String dateTo) {

        public boolean isEmpty() {
            return isBlank(documentClass) && isBlank(filePlanReference) && isBlank(dateFrom) && isBlank(dateTo);
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }

    public List<SearchHit> search(List<String> visibleOrgUnitIds, String query, Filters filters,
                                  int page, int size) {
        boolean hasQuery = query != null && !query.isBlank();
        if (!hasQuery && filters.isEmpty()) {
            throw new IllegalArgumentException("at least a query or one filter is required");
        }
        if (visibleOrgUnitIds.isEmpty()) {
            return List.of();
        }
        String documentClass = normalized(filters.documentClass());
        String filePlanReference = trimmedOrNull(filters.filePlanReference());
        String dateFrom = trimmedOrNull(filters.dateFrom());
        String dateTo = trimmedOrNull(filters.dateTo());
        int offset = page * size;
        String orgUnitIdsJson = SqlJson.array(visibleOrgUnitIds);

        List<IndexedDocumentRepository.SearchHitRow> rows = hasQuery
                ? index.searchFullText(toFtsQuery(query), orgUnitIdsJson,
                        documentClass, filePlanReference, dateFrom, dateTo, size, offset)
                : index.searchByFilters(orgUnitIdsJson,
                        documentClass, filePlanReference, dateFrom, dateTo, size, offset);
        return rows.stream()
                .map(row -> new SearchHit(row.getDocumentId(), row.getName(), row.getOrgUnitId(),
                        row.getSnippet()))
                .toList();
    }

    /** Quote each term so user input can never inject FTS5 query syntax. */
    static String toFtsQuery(String query) {
        return Arrays.stream(query.trim().split("\\s+"))
                .map(term -> "\"" + term.replace("\"", "") + "\"")
                .collect(Collectors.joining(" "));
    }

    private static String normalized(String value) {
        String trimmed = trimmedOrNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(java.util.Locale.ROOT);
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
