package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "document")
public class Document {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "uploaded_by", nullable = false)
    private String uploadedBy;

    @Column(name = "org_unit_id", nullable = false)
    private String orgUnitId;

    @Column(name = "ingest_date", nullable = false)
    private long ingestDate;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected Document() {
    }

    public Document(String id, String name, String uploadedBy, String orgUnitId, long ingestDate) {
        this.id = id;
        this.name = name;
        this.uploadedBy = uploadedBy;
        this.orgUnitId = orgUnitId;
        this.ingestDate = ingestDate;
        this.createdAt = ingestDate;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public long getIngestDate() {
        return ingestDate;
    }

    public long getCreatedAt() {
        return createdAt;
    }
}
