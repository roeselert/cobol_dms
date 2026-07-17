package de.dms.organization.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.organization.control.Hierarchy;
import de.dms.organization.entity.OrgUnit;
import de.dms.crosscutting.platform.control.ForbiddenException;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orgs")
public class OrgsController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final Hierarchy hierarchy;

    public OrgsController(CurrentUser currentUser, Authorization authorization, Hierarchy hierarchy) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.hierarchy = hierarchy;
    }

    public record OrgUnitDto(String id, String name, String parentId, String path) {
    }

    public record CreateOrgRequest(String name, String parentId) {
    }

    public record UpdateOrgRequest(String name, String parentId) {
    }

    @GetMapping
    public List<OrgUnitDto> list() {
        UserRef user = currentUser.require();
        List<String> visible = authorization.visibleOrgUnitIds(user);
        return hierarchy.all().stream()
                .filter(unit -> visible.contains(unit.getId()))
                .map(OrgsController::toDto)
                .toList();
    }

    @PostMapping
    public ResponseEntity<OrgUnitDto> create(@RequestBody CreateOrgRequest request) {
        UserRef user = currentUser.require();
        if (request.parentId() == null || request.parentId().isBlank()) {
            // creating a top-level unit is reserved for bootstrap admins
            if (!user.bootstrapAdmin()) {
                throw new ForbiddenException("only bootstrap admins may create root units");
            }
        } else {
            authorization.requireAdmin(user, ResourceRef.orgUnit(request.parentId()));
        }
        OrgUnit created = hierarchy.create(request.name(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public OrgUnitDto update(@PathVariable String id, @RequestBody UpdateOrgRequest request) {
        UserRef user = currentUser.require();
        authorization.requireAdmin(user, ResourceRef.orgUnit(id));
        OrgUnit unit = hierarchy.require(id);
        if (request.name() != null && !request.name().isBlank()) {
            unit = hierarchy.rename(id, request.name());
        }
        if (request.parentId() != null && !request.parentId().isBlank()
                && !request.parentId().equals(unit.getParentId())) {
            authorization.requireAdmin(user, ResourceRef.orgUnit(request.parentId()));
            unit = hierarchy.move(id, request.parentId());
        }
        return toDto(unit);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UserRef user = currentUser.require();
        authorization.requireDelete(user, ResourceRef.orgUnit(id));
        hierarchy.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static OrgUnitDto toDto(OrgUnit unit) {
        return new OrgUnitDto(unit.getId(), unit.getName(), unit.getParentId(), unit.getPath());
    }
}
