package de.dms.crosscutting.accesscontrol.control;

import java.util.List;
import java.util.Optional;

/**
 * Read-only directory port the authorization chokepoint uses to resolve a
 * user's grants and the org-unit hierarchy. Implemented by the organization BC
 * (Dependency Inversion) so accesscontrol stays a leaf — it never imports
 * organization's entities or repositories.
 */
public interface OrgDirectory {

    /** A user's membership on an org unit, carrying that unit's materialized path. */
    record Grant(String orgUnitId, String path, Role role) {
    }

    /** All grants held by the user (one per membership), each with its unit path. */
    List<Grant> grantsForUser(String userId);

    /** Materialized path of an org unit, or empty if it does not exist. */
    Optional<String> pathOf(String orgUnitId);

    /** Ids of every org unit whose path starts with the given prefix (unit + sub-units). */
    List<String> descendantOrgUnitIds(String pathPrefix);

    /** Ids of every org unit (bootstrap-admin visibility). */
    List<String> allOrgUnitIds();
}
