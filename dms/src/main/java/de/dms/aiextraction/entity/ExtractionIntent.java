package de.dms.aiextraction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A processing intent the AI may recognize; its fields drive the extraction. */
@Entity
@Table(name = "extraction_intent")
public class ExtractionIntent {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected ExtractionIntent() {
    }

    public ExtractionIntent(String id, String name, String description, long createdAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void rename(String name, String description) {
        this.name = name;
        this.description = description;
    }
}
