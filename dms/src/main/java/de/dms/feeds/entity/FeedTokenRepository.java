package de.dms.feeds.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FeedTokenRepository extends JpaRepository<FeedToken, String> {

    Optional<FeedToken> findByTokenHash(String tokenHash);

    List<FeedToken> findByUserId(String userId);
}
