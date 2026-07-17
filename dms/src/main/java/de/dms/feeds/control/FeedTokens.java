package de.dms.feeds.control;

import de.dms.feeds.entity.FeedToken;
import de.dms.feeds.entity.FeedTokenRepository;
import de.dms.crosscutting.platform.control.DmsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues / validates / revokes opaque feed tokens, stored only as HMAC-SHA256.
 * Tokens expire {@code dms.feed.token-ttl-days} after issue (they ride in
 * URLs, which land in logs and history — a leaked feed URL goes stale).
 */
@Service
public class FeedTokens {

    static final String DEFAULT_SECRET = "change-me-in-production";

    private final FeedTokenRepository tokens;
    private final DmsProperties properties;
    private final byte[] secret;
    private final long ttlMillis;
    private final SecureRandom random = new SecureRandom();

    public FeedTokens(FeedTokenRepository tokens, DmsProperties properties) {
        this.tokens = tokens;
        this.properties = properties;
        this.secret = properties.feed().tokenSecret().getBytes(StandardCharsets.UTF_8);
        this.ttlMillis = Duration.ofDays(properties.feed().tokenTtlDays()).toMillis();
    }

    /** Fails closed: a production (oidc) deployment must not run with the default HMAC secret. */
    @PostConstruct
    void refuseDefaultSecretInProduction() {
        if (!properties.security().devMode()
                && DEFAULT_SECRET.equals(properties.feed().tokenSecret())) {
            throw new IllegalStateException(
                    "dms.feed.token-secret still has its default value; set DMS_FEED_TOKEN_SECRET "
                            + "(oidc mode refuses to start with the default secret)");
        }
    }

    public record IssuedToken(String id, String token) {
    }

    /** The raw token is returned exactly once and never persisted. */
    public IssuedToken issue(String userId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        FeedToken entity = tokens.save(new FeedToken(UUID.randomUUID().toString(), userId, hmac(token),
                Instant.now().toEpochMilli()));
        return new IssuedToken(entity.getId(), token);
    }

    /** Resolves a presented token to its user; empty for unknown, revoked or expired tokens. */
    public Optional<String> resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.findByTokenHash(hmac(token))
                .filter(t -> !t.isRevoked())
                .filter(this::notExpired)
                .map(FeedToken::getUserId);
    }

    public void revoke(String tokenId, String userId) {
        tokens.findById(tokenId)
                .filter(t -> t.getUserId().equals(userId))
                .ifPresent(t -> {
                    t.revoke(Instant.now().toEpochMilli());
                    tokens.save(t);
                });
    }

    private boolean notExpired(FeedToken token) {
        return token.getCreatedAt() + ttlMillis > Instant.now().toEpochMilli();
    }

    private String hmac(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }
}
