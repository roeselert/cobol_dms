package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AkteRepository extends JpaRepository<Akte, String> {

    Optional<Akte> findByFilePlanReference(String filePlanReference);

    /** Akten of the visible org units; the visibility list rides as one JSON bind parameter. */
    @Query(nativeQuery = true, value = """
            SELECT * FROM akte
            WHERE org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))""")
    List<Akte> findByOrgUnits(@Param("orgUnitIdsJson") String orgUnitIdsJson);

    /** One document row of an Akte's paginated, ACL-filtered listing. */
    interface AkteDocumentRow {
        String getDocumentId();

        String getName();

        long getIngestDate();

        String getStatus();
    }

    /**
     * Documents of an Akte ordered by ingest date; the caller's visibility
     * predicate is part of the query (ACL push-down, S-1).
     */
    @Query(nativeQuery = true, value = """
            SELECT d.id AS documentId, d.name AS name, d.ingest_date AS ingestDate, ds.status AS status
            FROM document d
            JOIN document_file_plan_reference f ON f.document_id = d.id
            LEFT JOIN document_status ds ON ds.document_id = d.id
            WHERE f.akte_id = :akteId
              AND d.org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))
            ORDER BY d.ingest_date LIMIT :limit OFFSET :offset""")
    List<AkteDocumentRow> findAkteDocuments(@Param("akteId") String akteId,
                                            @Param("orgUnitIdsJson") String orgUnitIdsJson,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);
}
