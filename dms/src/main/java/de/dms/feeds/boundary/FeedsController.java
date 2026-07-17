package de.dms.feeds.boundary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentIntent;
import de.dms.documents.entity.DocumentIntentRepository;
import de.dms.feeds.control.FeedTokens;
import de.dms.feeds.control.InboxFeed;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/feeds")
public class FeedsController {

    private static final DateTimeFormatter RFC1123 =
            DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);

    private final FeedTokens feedTokens;
    private final InboxFeed inboxFeed;
    private final CurrentUser currentUser;
    private final DocumentIntentRepository intents;
    private final ObjectMapper objectMapper;

    public FeedsController(FeedTokens feedTokens, InboxFeed inboxFeed, CurrentUser currentUser,
                           DocumentIntentRepository intents, ObjectMapper objectMapper) {
        this.feedTokens = feedTokens;
        this.inboxFeed = inboxFeed;
        this.currentUser = currentUser;
        this.intents = intents;
        this.objectMapper = objectMapper;
    }

    /** RSS 2.0 inbox feed, authenticated by the opaque token (401 otherwise). */
    @GetMapping(value = "/inbox.rss", produces = "application/rss+xml")
    public ResponseEntity<String> inbox(@RequestParam(required = false) String token,
                                        HttpServletRequest request) {
        Optional<String> userId = feedTokens.resolveUserId(token);
        if (userId.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Optional<List<Document>> docs = inboxFeed.recentVisibleDocuments(userId.get());
        if (docs.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String base = request.getScheme() + "://" + request.getServerName()
                + portSuffix(request) + "/";
        return ResponseEntity.ok(rss(base, docs.get()));
    }

    @PostMapping("/token")
    public Map<String, String> issueToken(HttpServletRequest request) {
        UserRef user = currentUser.require();
        FeedTokens.IssuedToken issued = feedTokens.issue(user.id());
        String base = request.getScheme() + "://" + request.getServerName() + portSuffix(request);
        return Map.of(
                "id", issued.id(),
                "token", issued.token(),
                "url", base + "/api/v1/feeds/inbox.rss?token=" + issued.token());
    }

    @DeleteMapping("/token/{id}")
    public ResponseEntity<Void> revoke(@PathVariable String id) {
        UserRef user = currentUser.require();
        feedTokens.revoke(id, user.id());
        return ResponseEntity.noContent().build();
    }

    private static String portSuffix(HttpServletRequest request) {
        int port = request.getServerPort();
        boolean standard = ("http".equals(request.getScheme()) && port == 80)
                || ("https".equals(request.getScheme()) && port == 443);
        return standard ? "" : ":" + port;
    }

    private String rss(String baseUrl, List<Document> docs) {
        Map<String, DocumentIntent> intentByDocument = docs.isEmpty() ? Map.of()
                : intents.findByDocumentIdIn(docs.stream().map(Document::getId).toList()).stream()
                        .collect(Collectors.toMap(DocumentIntent::getDocumentId, Function.identity()));
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<rss version=\"2.0\"><channel>")
           .append("<title>DMS Inbox</title>")
           .append("<link>").append(escape(baseUrl)).append("</link>")
           .append("<description>Recently received documents</description>");
        for (Document doc : docs) {
            xml.append("<item>")
               .append("<title>").append(escape(doc.getName())).append("</title>")
               .append("<link>").append(escape(baseUrl + "api/v1/documents/" + doc.getId())).append("</link>")
               .append("<guid isPermaLink=\"false\">").append(escape(doc.getId())).append("</guid>")
               .append("<pubDate>").append(RFC1123.format(Instant.ofEpochMilli(doc.getIngestDate())))
               .append("</pubDate>");
            appendIntent(xml, intentByDocument.get(doc.getId()));
            xml.append("</item>");
        }
        return xml.append("</channel></rss>").toString();
    }

    /** The detected intent rides as the item's category, its fields in the description. */
    private void appendIntent(StringBuilder xml, DocumentIntent intent) {
        if (intent == null) {
            return;
        }
        xml.append("<category>").append(escape(intent.getName())).append("</category>");
        StringBuilder description = new StringBuilder("Intent: ").append(intent.getName());
        parseFields(intent).forEach((name, value) ->
                description.append(" · ").append(name).append(": ").append(value));
        xml.append("<description>").append(escape(description.toString())).append("</description>");
    }

    private Map<String, String> parseFields(DocumentIntent intent) {
        if (intent.getFieldsJson() == null) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(intent.getFieldsJson(),
                    new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse stored intent fields", e);
        }
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
