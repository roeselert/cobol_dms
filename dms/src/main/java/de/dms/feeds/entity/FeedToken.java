package de.dms.feeds.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Opaque, revocable feed token; only the HMAC is stored (US-04). */
@Entity
@Table(name = "feed_token")
public class FeedToken {

    @Id
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "revoked_at")
    private Long revokedAt;

    protected FeedToken() {
    }

    public FeedToken(String id, String userId, String tokenHash, long createdAt) {
        this.id = id;
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(long now) {
        this.revokedAt = now;
    }
}
