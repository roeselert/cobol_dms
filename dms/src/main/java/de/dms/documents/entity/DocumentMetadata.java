package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Versioned standard metadata; version 0 marks an unconfirmed AI suggestion.
 * document_class holds a value from the configured controlled vocabulary
 * (validated in metadata.control, not by the type system).
 */
@Entity
@Table(name = "document_metadata")
public class DocumentMetadata {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "document_date")
    private String documentDate;

    @Column(name = "document_class")
    private String documentClass;

    @Column(name = "extracted_by_ai", nullable = false)
    private boolean extractedByAi;

    @Column(name = "updated_by")
    private String updatedBy;

    /** Set by the ingest pipeline when extraction found nothing or errored. */
    @Enumerated(EnumType.STRING)
    @Column(name = "indexing_flag")
    private IndexingFlag indexingFlag;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    protected DocumentMetadata() {
    }

    public DocumentMetadata(String documentId, String documentDate, String documentClass,
                            boolean extractedByAi, String updatedBy, int version, long now) {
        this.documentId = documentId;
        this.documentDate = documentDate;
        this.documentClass = documentClass;
        this.extractedByAi = extractedByAi;
        this.updatedBy = updatedBy;
        this.version = version;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getDocumentDate() {
        return documentDate;
    }

    public String getDocumentClass() {
        return documentClass;
    }

    public boolean isExtractedByAi() {
        return extractedByAi;
    }

    public int getVersion() {
        return version;
    }

    public IndexingFlag getIndexingFlag() {
        return indexingFlag;
    }

    public void flagForIndexing(IndexingFlag flag) {
        this.indexingFlag = flag;
    }

    public void update(String documentDate, String documentClass, boolean extractedByAi,
                       String updatedBy, long now) {
        this.documentDate = documentDate;
        this.documentClass = documentClass;
        this.extractedByAi = extractedByAi;
        this.updatedBy = updatedBy;
        this.indexingFlag = null;   // manual indexing performed
        this.version++;
        this.updatedAt = now;
    }
}
