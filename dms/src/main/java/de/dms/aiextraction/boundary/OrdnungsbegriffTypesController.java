package de.dms.aiextraction.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.crosscutting.accesscontrol.entity.AuditAction;
import de.dms.aiextraction.control.OrdnungsbegriffCatalog;
import de.dms.aiextraction.entity.OrdnungsbegriffType;
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

/**
 * CRUD for the Ordnungsbegriff type catalog. Readable by every authenticated
 * user (the list includes inactive types so the admin UI can re-activate
 * them); mutations are reserved for bootstrap admins because the catalog is
 * deployment-global.
 */
@RestController
@RequestMapping("/api/v1/ordnungsbegriff-types")
public class OrdnungsbegriffTypesController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final OrdnungsbegriffCatalog catalog;

    public OrdnungsbegriffTypesController(CurrentUser currentUser, Authorization authorization,
                                          OrdnungsbegriffCatalog catalog) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.catalog = catalog;
    }

    public record OrdnungsbegriffTypeDto(String id, String name, String description, boolean active) {
    }

    public record SaveRequest(String name, String description, Boolean active) {
    }

    @GetMapping
    public List<OrdnungsbegriffTypeDto> list() {
        currentUser.require();
        return catalog.all().stream().map(OrdnungsbegriffTypesController::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<OrdnungsbegriffTypeDto> create(@RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.ordnungsbegriffType(null));
        OrdnungsbegriffType created = catalog.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public OrdnungsbegriffTypeDto update(@PathVariable String id, @RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.ordnungsbegriffType(id));
        boolean active = request.active() == null || request.active();
        return toDto(catalog.update(id, request.name(), request.description(), active));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.DELETE, ResourceRef.ordnungsbegriffType(id));
        catalog.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static OrdnungsbegriffTypeDto toDto(OrdnungsbegriffType type) {
        return new OrdnungsbegriffTypeDto(type.getId(), type.getName(), type.getDescription(), type.isActive());
    }
}
