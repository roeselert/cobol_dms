package de.dms.organization.control;

import de.dms.organization.entity.OrgUnit;
import de.dms.organization.entity.OrgUnitRepository;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Maintains the cycle-free org hierarchy and its materialized path (US-05). */
@Service
public class Hierarchy {

    private final OrgUnitRepository orgUnits;

    public Hierarchy(OrgUnitRepository orgUnits) {
        this.orgUnits = orgUnits;
    }

    public OrgUnit create(String name, String parentId) {
        if (name == null || name.isBlank()) {
            throw new UnprocessableException("name is required");
        }
        String id = UUID.randomUUID().toString();
        String parentPath = "/";
        if (parentId != null && !parentId.isBlank()) {
            OrgUnit parent = require(parentId);
            parentPath = parent.getPath();
        } else {
            parentId = null;
        }
        return orgUnits.save(new OrgUnit(id, name.trim(), parentId, parentPath + id + "/"));
    }

    public OrgUnit rename(String id, String name) {
        if (name == null || name.isBlank()) {
            throw new UnprocessableException("name is required");
        }
        OrgUnit unit = require(id);
        unit.setName(name.trim());
        return orgUnits.save(unit);
    }

    public OrgUnit move(String id, String newParentId) {
        OrgUnit unit = require(id);
        OrgUnit newParent = require(newParentId);
        if (newParent.getPath().startsWith(unit.getPath())) {
            throw new ConflictException("moving a unit below itself would create a cycle");
        }
        String oldPrefix = unit.getPath();
        String newPrefix = newParent.getPath() + unit.getId() + "/";
        List<OrgUnit> subtree = orgUnits.findByPathStartingWith(oldPrefix);
        for (OrgUnit node : subtree) {
            node.setPath(newPrefix + node.getPath().substring(oldPrefix.length()));
        }
        unit.setParentId(newParent.getId());
        unit.setPath(newPrefix);
        orgUnits.saveAll(subtree);
        return orgUnits.save(unit);
    }

    public void delete(String id) {
        OrgUnit unit = require(id);
        if (orgUnits.existsByParentId(id)) {
            throw new ConflictException("unit has sub-units");
        }
        if (orgUnits.countMemberships(id) > 0) {
            throw new ConflictException("unit has members");
        }
        if (orgUnits.countAktenAndDocuments(id) > 0) {
            throw new ConflictException("unit has documents or akten");
        }
        orgUnits.delete(unit);
    }

    public OrgUnit require(String id) {
        return orgUnits.findById(id).orElseThrow(() -> new NotFoundException("org unit " + id));
    }

    public List<OrgUnit> all() {
        return orgUnits.findAll();
    }
}
