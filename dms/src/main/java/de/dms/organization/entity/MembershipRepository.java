package de.dms.organization.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MembershipRepository extends JpaRepository<Membership, String> {

    List<Membership> findByUserId(String userId);

    List<Membership> findByOrgUnitId(String orgUnitId);

    Optional<Membership> findByUserIdAndOrgUnitId(String userId, String orgUnitId);
}
