package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One Ordnungsbegriff (business reference identifier) extracted for a
 * document. type_name is a snapshot of the configured type's name at
 * extraction time — deliberately no FK, so catalog edits never touch
 * stored metadata.
 */
@Entity
@Table(name = "document_ordnungsbegriff")
public class DocumentOrdnungsbegriff {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(nullable = false)
    private String value;

    @Column(name = "extracted_by_ai", nullable = false)
    private boolean extractedByAi;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected DocumentOrdnungsbegriff() {
    }

    public DocumentOrdnungsbegriff(String id, String documentId, String typeName, String value,
                                   boolean extractedByAi, long createdAt) {
        this.id = id;
        this.documentId = documentId;
        this.typeName = typeName;
        this.value = value;
        this.extractedByAi = extractedByAi;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getTypeName() {
        return typeName;
    }

    public String getValue() {
        return value;
    }

    public boolean isExtractedByAi() {
        return extractedByAi;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
