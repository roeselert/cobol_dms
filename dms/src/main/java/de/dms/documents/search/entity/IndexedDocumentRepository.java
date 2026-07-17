package de.dms.documents.search.entity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface IndexedDocumentRepository extends JpaRepository<IndexedDocument, String> {

    /** Source data for (re)indexing one document, joined across the owning BCs' tables. */
    interface IndexSource {
        String getName();

        String getOrgUnitId();

        String getDocumentClass();

        String getFilePlanReference();
    }

    /** A search hit including the FTS5 snippet with <mark> highlights. */
    interface SearchHitRow {
        String getDocumentId();

        String getName();

        String getOrgUnitId();

        String getSnippet();
    }

    @Query(nativeQuery = true, value = """
            SELECT d.name AS name, d.org_unit_id AS orgUnitId,
                   m.document_class AS documentClass, f.file_plan_reference AS filePlanReference
            FROM document d
            LEFT JOIN document_metadata m ON m.document_id = d.id
            LEFT JOIN document_file_plan_reference f ON f.document_id = d.id
            WHERE d.id = :documentId""")
    Optional<IndexSource> findIndexSource(@Param("documentId") String documentId);

    @Query(nativeQuery = true, value = """
            SELECT content_text FROM document_fts WHERE document_id = :documentId LIMIT 1""")
    Optional<String> findContentText(@Param("documentId") String documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = "DELETE FROM document_fts WHERE document_id = :documentId")
    void deleteByDocumentId(@Param("documentId") String documentId);

    @Modifying
    @Transactional
    @Query(nativeQuery = true, value = """
            INSERT INTO document_fts (document_id, org_unit_id, name, document_class,
                                      file_plan_reference, content_text)
            VALUES (:documentId, :orgUnitId, :name, :documentClass, :filePlanReference, :contentText)""")
    void insert(@Param("documentId") String documentId, @Param("orgUnitId") String orgUnitId,
                @Param("name") String name, @Param("documentClass") String documentClass,
                @Param("filePlanReference") String filePlanReference,
                @Param("contentText") String contentText);

    /**
     * Full-text search with ACL push-down: the caller's visible-org-unit
     * predicate is part of the SQL, never applied post-hoc, so a forbidden
     * hit is never produced (S-1). Null filter parameters pass through.
     */
    @Query(nativeQuery = true, value = """
            SELECT f.document_id AS documentId, d.name AS name, d.org_unit_id AS orgUnitId,
                   snippet(document_fts, 5, '<mark>', '</mark>', '…', 12) AS snippet
            FROM document_fts f
            JOIN document d ON d.id = f.document_id
            LEFT JOIN document_metadata m ON m.document_id = d.id
            LEFT JOIN document_file_plan_reference fp ON fp.document_id = d.id
            WHERE document_fts MATCH :ftsQuery
              AND d.org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))
              AND (:documentClass IS NULL OR m.document_class = :documentClass)
              AND (:filePlanReference IS NULL OR fp.file_plan_reference = :filePlanReference)
              AND (:dateFrom IS NULL OR m.document_date >= :dateFrom)
              AND (:dateTo IS NULL OR m.document_date <= :dateTo)
            ORDER BY rank LIMIT :limit OFFSET :offset""")
    List<SearchHitRow> searchFullText(@Param("ftsQuery") String ftsQuery,
                                      @Param("orgUnitIdsJson") String orgUnitIdsJson,
                                      @Param("documentClass") String documentClass,
                                      @Param("filePlanReference") String filePlanReference,
                                      @Param("dateFrom") String dateFrom,
                                      @Param("dateTo") String dateTo,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    /** Metadata-only search (no full-text query) with the same ACL push-down. */
    @Query(nativeQuery = true, value = """
            SELECT d.id AS documentId, d.name AS name, d.org_unit_id AS orgUnitId, NULL AS snippet
            FROM document d
            LEFT JOIN document_metadata m ON m.document_id = d.id
            LEFT JOIN document_file_plan_reference fp ON fp.document_id = d.id
            WHERE d.org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))
              AND (:documentClass IS NULL OR m.document_class = :documentClass)
              AND (:filePlanReference IS NULL OR fp.file_plan_reference = :filePlanReference)
              AND (:dateFrom IS NULL OR m.document_date >= :dateFrom)
              AND (:dateTo IS NULL OR m.document_date <= :dateTo)
            ORDER BY d.ingest_date DESC LIMIT :limit OFFSET :offset""")
    List<SearchHitRow> searchByFilters(@Param("orgUnitIdsJson") String orgUnitIdsJson,
                                       @Param("documentClass") String documentClass,
                                       @Param("filePlanReference") String filePlanReference,
                                       @Param("dateFrom") String dateFrom,
                                       @Param("dateTo") String dateTo,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);
}
