package de.dms.conversion.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Durable queue row: attempts, backoff (available_at), lease (crash recovery). */
@Entity
@Table(name = "conversion_job")
public class ConversionJob {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobState status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "available_at", nullable = false)
    private long availableAt;

    @Column(name = "lease_until")
    private Long leaseUntil;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected ConversionJob() {
    }

    public ConversionJob(String id, String documentId, long now) {
        this.id = id;
        this.documentId = documentId;
        this.status = JobState.QUEUED;
        this.attempts = 0;
        this.availableAt = now;
        this.createdAt = now;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public JobState getStatus() {
        return status;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }
}
