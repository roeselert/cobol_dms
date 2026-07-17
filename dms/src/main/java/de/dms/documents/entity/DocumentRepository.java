package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, String> {

    /**
     * Newest documents of the visible org units. The visibility list is bound
     * as one JSON parameter (json_each) so it can never exceed SQLite's
     * bound-variable limit; limit/offset are clamped at the boundary.
     */
    @Query(nativeQuery = true, value = """
            SELECT * FROM document
            WHERE org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))
            ORDER BY ingest_date DESC LIMIT :limit OFFSET :offset""")
    List<Document> findRecentByOrgUnits(@Param("orgUnitIdsJson") String orgUnitIdsJson,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);
}
