package de.dms.crosscutting.accesscontrol.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_log_entry")
public class AuditLogEntry {

    @Id
    private String id;

    // deliberately not a FK — audit rows survive user deletion
    @Column(name = "user_id")
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditEffect effect;

    @Column(name = "timestamp", nullable = false)
    private long timestamp;

    @Column(name = "source_ip")
    private String sourceIp;

    protected AuditLogEntry() {
    }

    public AuditLogEntry(String id, String userId, AuditAction action, String resourceType,
                         String resourceId, AuditEffect effect, long timestamp, String sourceIp) {
        this.id = id;
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.effect = effect;
        this.timestamp = timestamp;
        this.sourceIp = sourceIp;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public AuditAction getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public AuditEffect getEffect() {
        return effect;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getSourceIp() {
        return sourceIp;
    }
}
