package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "rendition", uniqueConstraints = @UniqueConstraint(columnNames = {"document_id", "type"}))
public class Rendition {

    @Id
    private String id;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RenditionType type;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", nullable = false)
    private String checksumSha256;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    /** Tool that produced the bytes: upload | ocrmypdf | ghostscript | libreoffice | passthrough. */
    @Column(name = "producer")
    private String producer;

    protected Rendition() {
    }

    public Rendition(String id, String documentId, RenditionType type, String storageKey,
                     String mimeType, long sizeBytes, String checksumSha256, long createdAt,
                     String producer) {
        this.id = id;
        this.documentId = documentId;
        this.type = type;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.checksumSha256 = checksumSha256;
        this.createdAt = createdAt;
        this.producer = producer;
    }

    public String getId() {
        return id;
    }

    public String getDocumentId() {
        return documentId;
    }

    public RenditionType getType() {
        return type;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public String getProducer() {
        return producer;
    }
}
