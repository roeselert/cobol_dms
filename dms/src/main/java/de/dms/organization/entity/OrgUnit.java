package de.dms.organization.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Org unit with a materialized path of unit ids (e.g. "/id1/id2/") — the path
 * is id-based so renames never require a subtree rewrite; prefix matching
 * grants sub-unit visibility (S-1).
 */
@Entity
@Table(name = "org_unit")
public class OrgUnit {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "parent_id")
    private String parentId;

    @Column(nullable = false, unique = true)
    private String path;

    protected OrgUnit() {
    }

    public OrgUnit(String id, String name, String parentId, String path) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.path = path;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}
