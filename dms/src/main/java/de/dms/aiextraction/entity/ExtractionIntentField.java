package de.dms.aiextraction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One extraction field of an intent; name is the JSON key the model answers with. */
@Entity
@Table(name = "extraction_intent_field", uniqueConstraints = @UniqueConstraint(columnNames = {"intent_id", "name"}))
public class ExtractionIntentField {

    @Id
    private String id;

    @Column(name = "intent_id", nullable = false)
    private String intentId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String description;

    protected ExtractionIntentField() {
    }

    public ExtractionIntentField(String id, String intentId, String name, String description) {
        this.id = id;
        this.intentId = intentId;
        this.name = name;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getIntentId() {
        return intentId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
