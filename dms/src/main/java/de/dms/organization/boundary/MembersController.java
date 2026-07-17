package de.dms.organization.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.organization.control.Enrollment;
import de.dms.organization.entity.Membership;
import de.dms.crosscutting.accesscontrol.control.Role;
import de.dms.organization.entity.UserRepository;
import de.dms.crosscutting.platform.control.UnprocessableException;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/v1/orgs/{orgUnitId}/members")
public class MembersController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final Enrollment enrollment;
    private final UserRepository users;

    public MembersController(CurrentUser currentUser, Authorization authorization,
                             Enrollment enrollment, UserRepository users) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.enrollment = enrollment;
        this.users = users;
    }

    public record AssignRequest(String email, String role) {
    }

    public record MemberDto(String membershipId, String email, String role) {
    }

    @PostMapping
    public ResponseEntity<MemberDto> assign(@PathVariable String orgUnitId, @RequestBody AssignRequest request) {
        UserRef user = currentUser.require();
        authorization.requireAdmin(user, ResourceRef.orgUnit(orgUnitId));
        Membership membership = enrollment.assign(orgUnitId, request.email(), parseRole(request.role()));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(membership));
    }

    /** Revokes a membership; loses access to the unit and its whole subtree. */
    @DeleteMapping("/{membershipId}")
    public ResponseEntity<Void> remove(@PathVariable String orgUnitId, @PathVariable String membershipId) {
        UserRef user = currentUser.require();
        authorization.requireAdmin(user, ResourceRef.orgUnit(orgUnitId));
        enrollment.remove(orgUnitId, membershipId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<MemberDto> list(@PathVariable String orgUnitId) {
        UserRef user = currentUser.require();
        authorization.requireRead(user, ResourceRef.orgUnit(orgUnitId));
        return enrollment.membersOf(orgUnitId).stream().map(this::toDto).toList();
    }

    private MemberDto toDto(Membership membership) {
        String email = users.findById(membership.getUserId()).map(u -> u.getEmail()).orElse("?");
        return new MemberDto(membership.getId(), email, membership.getRole().name());
    }

    private static Role parseRole(String role) {
        if (role == null) {
            throw new UnprocessableException("role is required");
        }
        try {
            return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new UnprocessableException("unknown role: " + role);
        }
    }
}
