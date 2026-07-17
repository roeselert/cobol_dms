package de.dms.crosscutting.accesscontrol.control;

import de.dms.crosscutting.accesscontrol.entity.AuditAction;
import de.dms.crosscutting.accesscontrol.entity.AuditEffect;
import de.dms.crosscutting.platform.control.ForbiddenException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Single authorization chokepoint (S-1). Resolves a user's visible org units
 * (including sub-units via materialized-path prefix), evaluates the role
 * against the requested action and audits every decision (S-3).
 *
 * Denial mapping: a visibility denial throws {@link NotFoundException}
 * (uniform 403≡404 existence-hiding); an insufficient role on a visible
 * resource throws {@link ForbiddenException}.
 */
@Service
public class Authorization {

    private final OrgDirectory directory;
    private final AuditTrail auditTrail;

    public Authorization(OrgDirectory directory, AuditTrail auditTrail) {
        this.directory = directory;
        this.auditTrail = auditTrail;
    }

    public void requireRead(UserRef user, ResourceRef resource) {
        require(user, AuditAction.READ, Role.VIEWER, resource);
    }

    public void requireWrite(UserRef user, ResourceRef resource) {
        require(user, AuditAction.WRITE, Role.EDITOR, resource);
    }

    public void requireDelete(UserRef user, ResourceRef resource) {
        require(user, AuditAction.DELETE, Role.ADMIN, resource);
    }

    public void requireAdmin(UserRef user, ResourceRef resource) {
        require(user, AuditAction.WRITE, Role.ADMIN, resource);
    }

    /** Every org unit the user may read, including inherited sub-units. */
    public List<String> visibleOrgUnitIds(UserRef user) {
        if (user.bootstrapAdmin()) {
            return directory.allOrgUnitIds();
        }
        Set<String> visible = new LinkedHashSet<>();
        for (OrgDirectory.Grant grant : directory.grantsForUser(user.id())) {
            visible.addAll(directory.descendantOrgUnitIds(grant.path()));
        }
        return List.copyOf(visible);
    }

    /**
     * Global catalogs (document classes, extraction intents) have no owning
     * org unit; managing them is reserved for bootstrap admins. Audited like
     * every other decision. 403 (not 404) is correct here — the catalogs are
     * readable by every authenticated user, so there is nothing to hide.
     */
    public void requireBootstrapAdmin(UserRef user, AuditAction action, ResourceRef resource) {
        if (!user.bootstrapAdmin()) {
            auditTrail.record(user, action, resource, AuditEffect.DENY);
            throw new ForbiddenException("only bootstrap admins may manage " + resource.type());
        }
        auditTrail.record(user, action, resource, AuditEffect.ALLOW);
    }

    public boolean isBootstrapOrAnyAdmin(UserRef user) {
        return user.bootstrapAdmin()
                || directory.grantsForUser(user.id()).stream().anyMatch(g -> g.role() == Role.ADMIN);
    }

    private void require(UserRef user, AuditAction action, Role needed, ResourceRef resource) {
        Optional<Role> effective = effectiveRole(user, resource.orgUnitId());
        if (effective.isEmpty()) {
            auditTrail.record(user, action, resource, AuditEffect.DENY);
            // existence-hiding: indistinguishable from a nonexistent resource
            throw new NotFoundException(resource.type() + " " + resource.id());
        }
        if (!effective.get().atLeast(needed)) {
            auditTrail.record(user, action, resource, AuditEffect.DENY);
            throw new ForbiddenException("insufficient role for " + action);
        }
        auditTrail.record(user, action, resource, AuditEffect.ALLOW);
    }

    /** Highest role granted on the resource's unit by any membership on it or an ancestor. */
    private Optional<Role> effectiveRole(UserRef user, String orgUnitId) {
        if (user.bootstrapAdmin()) {
            return Optional.of(Role.ADMIN);
        }
        Optional<String> resourcePath = directory.pathOf(orgUnitId);
        if (resourcePath.isEmpty()) {
            return Optional.empty();
        }
        Role best = null;
        for (OrgDirectory.Grant grant : directory.grantsForUser(user.id())) {
            if (resourcePath.get().startsWith(grant.path())) {
                if (best == null || grant.role().atLeast(best)) {
                    best = grant.role();
                }
            }
        }
        return Optional.ofNullable(best);
    }
}
