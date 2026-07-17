package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The detected intent of a document: the extraction model picks the single
 * best-matching configured intent and extracts that intent's fields. name is
 * a snapshot of the catalog intent's name at extraction time — deliberately
 * no FK, so catalog edits never touch stored metadata. The extracted field
 * values ride along as a JSON object string (display-only).
 */
@Entity
@Table(name = "document_intent")
public class DocumentIntent {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Column(nullable = false)
    private String name;

    @Column(name = "fields_json")
    private String fieldsJson;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected DocumentIntent() {
    }

    public DocumentIntent(String documentId, String name, String fieldsJson, long createdAt) {
        this.documentId = documentId;
        this.name = name;
        this.fieldsJson = fieldsJson;
        this.createdAt = createdAt;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getName() {
        return name;
    }

    public String getFieldsJson() {
        return fieldsJson;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
