package de.dms.documents.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry of the controlled vocabulary; the description feeds the AI prompt. */
@Entity
@Table(name = "document_class")
public class DocumentClass {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    protected DocumentClass() {
    }

    public DocumentClass(String id, String name, String description, long createdAt) {
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
