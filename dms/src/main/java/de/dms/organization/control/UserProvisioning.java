package de.dms.organization.control;

import de.dms.organization.entity.User;
import de.dms.organization.entity.UserRepository;
import de.dms.organization.entity.UserStatus;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.UUID;

/** Provisions/updates the local {@link User} from OIDC claims on first login (US-06). */
@Service
public class UserProvisioning {

    private final UserRepository users;

    public UserProvisioning(UserRepository users) {
        this.users = users;
    }

    public User ensure(String email, String displayName) {
        String normalized = email.toLowerCase(Locale.ROOT).trim();
        User user = users.findByEmail(normalized).orElse(null);
        if (user == null) {
            return users.save(new User(UUID.randomUUID().toString(), normalized, displayName, UserStatus.ACTIVE));
        }
        boolean dirty = false;
        if (user.getStatus() == UserStatus.INVITED) {
            user.setStatus(UserStatus.ACTIVE);
            dirty = true;
        }
        if (displayName != null && !displayName.equals(user.getDisplayName())) {
            user.setDisplayName(displayName);
            dirty = true;
        }
        return dirty ? users.save(user) : user;
    }
}
