package de.dms.documents.search.control;

import de.dms.documents.search.entity.IndexedDocumentRepository;
import org.springframework.stereotype.Service;

/**
 * Maintains the FTS5 projection of a document (metadata + extracted text).
 * The projection carries the org_unit_id so the ACL predicate can be part of
 * the search query itself (S-1).
 */
@Service
public class SearchIndexer {

    private final IndexedDocumentRepository index;

    public SearchIndexer(IndexedDocumentRepository index) {
        this.index = index;
    }

    /** Re-projects one document; pass extractedText=null to keep any previously indexed text. */
    public void index(String documentId, String extractedText) {
        index.findIndexSource(documentId).ifPresent(source -> {
            String contentText = extractedText != null
                    ? extractedText
                    : index.findContentText(documentId).orElse("");
            index.deleteByDocumentId(documentId);
            index.insert(documentId,
                    source.getOrgUnitId(),
                    source.getName(),
                    source.getDocumentClass() == null ? "" : source.getDocumentClass(),
                    source.getFilePlanReference() == null ? "" : source.getFilePlanReference(),
                    contentText == null ? "" : contentText);
        });
    }
}
