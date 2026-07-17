package de.dms.documents.control;

/**
 * Port for scheduling async PDF/A conversion of a freshly uploaded document.
 * Implemented by the conversion BC (Dependency Inversion) so the documents BC
 * does not depend on conversion. The call runs inside the upload transaction,
 * so the conversion job is committed atomically with the Document (R-1).
 */
public interface ConversionEnqueuer {

    void enqueue(String documentId);

    /**
     * Re-run the conversion → AI classification → indexing pipeline for a
     * document that has already been ingested. Used to recover a document
     * whose job reached the terminal FAILED state, or to re-classify a
     * document whose earlier extraction was skipped or incomplete. The
     * existing durable job row is reset to a fresh set of attempts; if none
     * exists a new job is enqueued. Reprocessing is idempotent — the PDF/A
     * rendition uses a deterministic storage key, so a re-run overwrites
     * rather than duplicates.
     */
    void reprocess(String documentId);
}
