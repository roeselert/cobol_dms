package de.dms.organization.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.organization.entity.MembershipRepository;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
public class UsersController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final MembershipRepository memberships;

    public UsersController(CurrentUser currentUser, Authorization authorization,
                           MembershipRepository memberships) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.memberships = memberships;
    }

    public record MembershipDto(String orgUnitId, String role) {
    }

    public record MeDto(String id, String email, boolean admin, List<MembershipDto> memberships) {
    }

    @GetMapping("/me")
    public MeDto me() {
        UserRef user = currentUser.require();
        List<MembershipDto> mine = memberships.findByUserId(user.id()).stream()
                .map(m -> new MembershipDto(m.getOrgUnitId(), m.getRole().name()))
                .toList();
        return new MeDto(user.id(), user.email(), authorization.isBootstrapOrAnyAdmin(user), mine);
    }
}
