package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** File record grouping all documents that share one Ordnungsbegriff (US-08). */
@Entity
@Table(name = "akte")
public class Akte {

    @Id
    private String id;

    @Column(name = "file_plan_reference", nullable = false, unique = true)
    private String filePlanReference;

    @Column(name = "org_unit_id", nullable = false)
    private String orgUnitId;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected Akte() {
    }

    public Akte(String id, String filePlanReference, String orgUnitId, long createdAt) {
        this.id = id;
        this.filePlanReference = filePlanReference;
        this.orgUnitId = orgUnitId;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getFilePlanReference() {
        return filePlanReference;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }
}
