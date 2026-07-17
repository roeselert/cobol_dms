package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document_status")
public class DocumentStatus {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentState status;

    @Column(name = "changed_by")
    private String changedBy;

    @Column(name = "updated_at", nullable = false)
    private long updatedAt;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected DocumentStatus() {
    }

    public DocumentStatus(String documentId, DocumentState status, String changedBy, long now) {
        this.documentId = documentId;
        this.status = status;
        this.changedBy = changedBy;
        this.updatedAt = now;
        this.createdAt = now;
    }

    public String getDocumentId() {
        return documentId;
    }

    public DocumentState getStatus() {
        return status;
    }

    public void transition(DocumentState newState, String changedBy, long now) {
        this.status = newState;
        this.changedBy = changedBy;
        this.updatedAt = now;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }
}
