package de.dms.feeds.control;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentRepository;
import de.dms.organization.entity.User;
import de.dms.organization.entity.UserRepository;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.crosscutting.platform.control.SqlJson;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Assembles the recent documents visible to the token's user. Reuses the
 * accesscontrol BC's visibility resolution, so the feed can never expose a
 * document the user could not see interactively (US-04, S-1).
 */
@Service
public class InboxFeed {

    private static final int FEED_SIZE = 50;

    private final UserRepository users;
    private final Authorization authorization;
    private final DocumentRepository documents;
    private final List<String> bootstrapAdmins;

    public InboxFeed(UserRepository users, Authorization authorization, DocumentRepository documents,
                     DmsProperties properties) {
        this.users = users;
        this.authorization = authorization;
        this.documents = documents;
        this.bootstrapAdmins = properties.security().bootstrapAdminEmails() == null
                ? List.of()
                : properties.security().bootstrapAdminEmails().stream()
                        .map(email -> email.toLowerCase(Locale.ROOT)).toList();
    }

    public Optional<List<Document>> recentVisibleDocuments(String userId) {
        return users.findById(userId).map(this::visibleDocuments);
    }

    private List<Document> visibleDocuments(User user) {
        UserRef ref = new UserRef(user.getId(), user.getEmail(), bootstrapAdmins.contains(user.getEmail()));
        List<String> visible = authorization.visibleOrgUnitIds(ref);
        if (visible.isEmpty()) {
            return List.of();
        }
        return documents.findRecentByOrgUnits(SqlJson.array(visible), FEED_SIZE, 0);
    }
}
