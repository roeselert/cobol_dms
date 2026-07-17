package de.dms.organization.control;

import de.dms.crosscutting.accesscontrol.control.OrgDirectory;
import de.dms.organization.entity.Membership;
import de.dms.organization.entity.MembershipRepository;
import de.dms.organization.entity.OrgUnit;
import de.dms.organization.entity.OrgUnitRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Organization-side adapter of the accesscontrol {@link OrgDirectory} port:
 * exposes memberships and the materialized-path hierarchy without leaking JPA
 * entities across the BC boundary. This is the only compile-time edge from
 * organization into accesscontrol's port (the dependency now flows one way).
 */
@Component
public class OrgDirectoryAdapter implements OrgDirectory {

    private final MembershipRepository memberships;
    private final OrgUnitRepository orgUnits;

    public OrgDirectoryAdapter(MembershipRepository memberships, OrgUnitRepository orgUnits) {
        this.memberships = memberships;
        this.orgUnits = orgUnits;
    }

    @Override
    public List<Grant> grantsForUser(String userId) {
        List<Grant> grants = new ArrayList<>();
        for (Membership membership : memberships.findByUserId(userId)) {
            orgUnits.findById(membership.getOrgUnitId()).ifPresent(unit ->
                    grants.add(new Grant(membership.getOrgUnitId(), unit.getPath(), membership.getRole())));
        }
        return grants;
    }

    @Override
    public Optional<String> pathOf(String orgUnitId) {
        return orgUnits.findById(orgUnitId).map(OrgUnit::getPath);
    }

    @Override
    public List<String> descendantOrgUnitIds(String pathPrefix) {
        return orgUnits.findByPathStartingWith(pathPrefix).stream().map(OrgUnit::getId).toList();
    }

    @Override
    public List<String> allOrgUnitIds() {
        return orgUnits.findAll().stream().map(OrgUnit::getId).toList();
    }
}
