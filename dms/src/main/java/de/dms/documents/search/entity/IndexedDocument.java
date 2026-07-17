package de.dms.documents.search.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Searchable projection of a document (FTS5 virtual table document_fts).
 * org_unit_id is carried so the ACL predicate can be pushed into the search
 * query itself (S-1). Rows are maintained by the SearchIndexer, one per
 * document.
 */
@Entity
@Table(name = "document_fts")
public class IndexedDocument {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "org_unit_id")
    private String orgUnitId;

    @Column(name = "name")
    private String name;

    @Column(name = "document_class")
    private String documentClass;

    @Column(name = "file_plan_reference")
    private String filePlanReference;

    @Column(name = "content_text")
    private String contentText;

    protected IndexedDocument() {
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getContentText() {
        return contentText;
    }
}
