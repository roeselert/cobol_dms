package de.dms.organization.entity;

import de.dms.crosscutting.accesscontrol.control.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "membership", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "org_unit_id"}))
public class Membership {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "org_unit_id", nullable = false)
    private String orgUnitId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    protected Membership() {
    }

    public Membership(String id, String userId, String orgUnitId, Role role) {
        this.id = id;
        this.userId = userId;
        this.orgUnitId = orgUnitId;
        this.role = role;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getOrgUnitId() {
        return orgUnitId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }
}
