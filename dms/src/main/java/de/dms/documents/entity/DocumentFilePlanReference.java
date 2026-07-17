package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** The document's Ordnungsbegriff — the key that determines its (single) Akte. */
@Entity
@Table(name = "document_file_plan_reference")
public class DocumentFilePlanReference {

    @Id
    @Column(name = "document_id")
    private String documentId;

    @Column(name = "file_plan_reference", nullable = false)
    private String filePlanReference;

    @Column(name = "akte_id")
    private String akteId;

    @Column(name = "extracted_by_ai", nullable = false)
    private boolean extractedByAi;

    @Column(name = "updated_by")
    private String updatedBy;

    @Column(nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected DocumentFilePlanReference() {
    }

    public DocumentFilePlanReference(String documentId, String filePlanReference, boolean extractedByAi,
                                     String updatedBy, int version, long now) {
        this.documentId = documentId;
        this.filePlanReference = filePlanReference;
        this.extractedByAi = extractedByAi;
        this.updatedBy = updatedBy;
        this.version = version;
        this.createdAt = now;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getFilePlanReference() {
        return filePlanReference;
    }

    public String getAkteId() {
        return akteId;
    }

    public boolean isExtractedByAi() {
        return extractedByAi;
    }

    public int getVersion() {
        return version;
    }

    public void update(String filePlanReference, String akteId, boolean extractedByAi, String updatedBy) {
        this.filePlanReference = filePlanReference;
        this.akteId = akteId;
        this.extractedByAi = extractedByAi;
        this.updatedBy = updatedBy;
        this.version++;
    }

    public void linkAkte(String akteId) {
        this.akteId = akteId;
    }
}
