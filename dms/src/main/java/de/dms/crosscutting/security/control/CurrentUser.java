package de.dms.crosscutting.security.control;

import de.dms.organization.control.UserProvisioning;
import de.dms.organization.entity.User;
import de.dms.crosscutting.platform.control.DmsProperties;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Resolves the authenticated {@link UserRef} from the Spring Security context.
 * AuthN itself (JWT parsing/validation) is owned by Spring Security; this
 * component only maps the validated principal to a local user, provisioning
 * it from the OIDC claims on first login.
 */
@Component
public class CurrentUser {

    private final UserProvisioning userProvisioning;
    private final List<String> bootstrapAdmins;

    public CurrentUser(UserProvisioning userProvisioning, DmsProperties properties) {
        this.userProvisioning = userProvisioning;
        this.bootstrapAdmins = properties.security().bootstrapAdminEmails() == null
                ? List.of()
                : properties.security().bootstrapAdminEmails().stream()
                        .map(email -> email.toLowerCase(Locale.ROOT)).toList();
    }

    public UserRef require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("no authenticated principal — security filter chain misconfigured");
        }
        String email;
        String displayName = null;
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            email = firstNonBlank(jwt.getClaimAsString("email"),
                    jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            displayName = jwt.getClaimAsString("name");
        } else {
            email = auth.getName();
        }
        User user = userProvisioning.ensure(email, displayName);
        return new UserRef(user.getId(), user.getEmail(), bootstrapAdmins.contains(user.getEmail()));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        throw new IllegalStateException("token carries no usable identity claim");
    }
}
