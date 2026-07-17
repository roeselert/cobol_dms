package de.dms.organization.control;

import de.dms.organization.entity.Membership;
import de.dms.organization.entity.MembershipRepository;
import de.dms.crosscutting.accesscontrol.control.Role;
import de.dms.organization.entity.User;
import de.dms.organization.entity.UserRepository;
import de.dms.organization.entity.UserStatus;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Assigns users to org units with a role; enforces uniqueness (US-06). */
@Service
public class Enrollment {

    private final UserRepository users;
    private final MembershipRepository memberships;
    private final Hierarchy hierarchy;

    public Enrollment(UserRepository users, MembershipRepository memberships, Hierarchy hierarchy) {
        this.users = users;
        this.memberships = memberships;
        this.hierarchy = hierarchy;
    }

    public Membership assign(String orgUnitId, String email, Role role) {
        if (email == null || email.isBlank()) {
            throw new UnprocessableException("email is required");
        }
        if (role == null) {
            throw new UnprocessableException("role is required");
        }
        hierarchy.require(orgUnitId);
        String normalized = email.toLowerCase(Locale.ROOT).trim();
        User user = users.findByEmail(normalized)
                .orElseGet(() -> users.save(
                        new User(UUID.randomUUID().toString(), normalized, null, UserStatus.INVITED)));
        memberships.findByUserIdAndOrgUnitId(user.getId(), orgUnitId).ifPresent(m -> {
            throw new ConflictException("user is already a member of this unit");
        });
        try {
            return memberships.save(
                    new Membership(UUID.randomUUID().toString(), user.getId(), orgUnitId, role));
        } catch (DataIntegrityViolationException e) {
            // lost the race against a concurrent identical assignment — same
            // outcome as the pre-check above (409, not 500)
            throw new ConflictException("user is already a member of this unit");
        }
    }

    public List<Membership> membersOf(String orgUnitId) {
        hierarchy.require(orgUnitId);
        return memberships.findByOrgUnitId(orgUnitId);
    }

    /**
     * Revokes a membership. A membership that does not exist or belongs to a
     * different org unit is uniformly a 404 (existence-hiding, US-09).
     */
    public void remove(String orgUnitId, String membershipId) {
        Membership membership = memberships.findById(membershipId)
                .filter(m -> m.getOrgUnitId().equals(orgUnitId))
                .orElseThrow(() -> new NotFoundException("membership " + membershipId));
        memberships.delete(membership);
    }
}
