package de.dms.conversion.entity;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ConversionJobRepository extends JpaRepository<ConversionJob, String> {

    /** A queue row joined with the human-readable name of its document. */
    interface JobWithDocument {
        String getId();

        String getDocumentId();

        String getDocumentName();

        String getStatus();

        int getAttempts();

        String getLastError();

        long getCreatedAt();
    }

    Optional<ConversionJob> findByDocumentId(String documentId);

    /** Every job whose document sits in one of the given org units (ACL pushed into the query). */
    @Query(nativeQuery = true, value = """
            SELECT j.id AS id, j.document_id AS documentId, d.name AS documentName,
                   j.status AS status, j.attempts AS attempts, j.last_error AS lastError,
                   j.created_at AS createdAt
            FROM conversion_job j JOIN document d ON d.id = j.document_id
            WHERE d.org_unit_id IN (SELECT value FROM json_each(:orgUnitIdsJson))""")
    List<JobWithDocument> findAllWithDocument(@Param("orgUnitIdsJson") String orgUnitIdsJson);

    @Query(nativeQuery = true, value = """
            SELECT * FROM conversion_job
            WHERE status = 'QUEUED' AND available_at <= :now
            ORDER BY created_at LIMIT 1""")
    Optional<ConversionJob> findNextQueued(@Param("now") long now);

    /** Guarded claim: returns 0 when another consumer took the job first. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'RUNNING', attempts = attempts + 1, lease_until = :leaseUntil
            WHERE id = :id AND status = 'QUEUED'""")
    int claim(@Param("id") String id, @Param("leaseUntil") long leaseUntil);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'DONE', lease_until = NULL, last_error = NULL
            WHERE id = :id""")
    void markDone(@Param("id") String id);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'QUEUED', lease_until = NULL, last_error = :error, available_at = :availableAt
            WHERE id = :id""")
    void requeue(@Param("id") String id, @Param("error") String error, @Param("availableAt") long availableAt);

    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'FAILED', lease_until = NULL, last_error = :error
            WHERE id = :id""")
    void markFailed(@Param("id") String id, @Param("error") String error);

    /** Re-queue jobs whose lease expired while RUNNING (crashed worker, R-2). */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'QUEUED', lease_until = NULL, available_at = :now
            WHERE status = 'RUNNING' AND lease_until IS NOT NULL AND lease_until < :now""")
    int requeueExpiredLeases(@Param("now") long now);

    /** Manual retry: reset a terminal (or done) job to a fresh set of attempts. */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(nativeQuery = true, value = """
            UPDATE conversion_job
            SET status = 'QUEUED', attempts = 0, lease_until = NULL, last_error = NULL, available_at = :now
            WHERE id = :id""")
    void resetForRetry(@Param("id") String id, @Param("now") long now);
}
