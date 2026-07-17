package de.dms.organization.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, String> {

    List<OrgUnit> findByPathStartingWith(String pathPrefix);

    boolean existsByParentId(String parentId);

    // The delete-emptiness checks span other BCs' tables; native queries keep
    // the BC dependency direction (membership -> organization) intact.

    @Query(nativeQuery = true, value = "SELECT COUNT(*) FROM membership WHERE org_unit_id = :orgUnitId")
    long countMemberships(@Param("orgUnitId") String orgUnitId);

    @Query(nativeQuery = true, value = """
            SELECT (SELECT COUNT(*) FROM akte WHERE org_unit_id = :orgUnitId)
                 + (SELECT COUNT(*) FROM document WHERE org_unit_id = :orgUnitId)""")
    long countAktenAndDocuments(@Param("orgUnitId") String orgUnitId);
}
