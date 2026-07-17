package de.dms.documents.boundary;

import de.dms.documents.control.ConversionEnqueuer;
import de.dms.documents.control.Ingestion;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentRepository;
import de.dms.documents.entity.DocumentState;
import de.dms.documents.entity.DocumentStatus;
import de.dms.documents.entity.DocumentStatusRepository;
import de.dms.documents.entity.Rendition;
import de.dms.documents.entity.RenditionRepository;
import de.dms.documents.entity.RenditionType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Transactional boundary of the documents BC: Document, its RECEIVED status,
 * the ORIGINAL rendition and the conversion job are committed atomically —
 * either the upload is fully registered or not at all (R-1, no orphans).
 */
@Component
public class DocumentsFacade {

    private final DocumentRepository documents;
    private final DocumentStatusRepository statuses;
    private final RenditionRepository renditions;
    private final ConversionEnqueuer conversionEnqueuer;

    public DocumentsFacade(DocumentRepository documents, DocumentStatusRepository statuses,
                           RenditionRepository renditions, ConversionEnqueuer conversionEnqueuer) {
        this.documents = documents;
        this.statuses = statuses;
        this.renditions = renditions;
        this.conversionEnqueuer = conversionEnqueuer;
    }

    @Transactional
    public Document registerUpload(String documentId, String filename, String userId, String orgUnitId,
                                   Ingestion.StoredBinary binary) {
        long now = Instant.now().toEpochMilli();
        Document document = documents.save(new Document(documentId, filename, userId, orgUnitId, now));
        statuses.save(new DocumentStatus(documentId, DocumentState.RECEIVED, userId, now));
        renditions.save(new Rendition(UUID.randomUUID().toString(), documentId, RenditionType.ORIGINAL,
                binary.storageKey(), binary.mimeType(), binary.sizeBytes(), binary.sha256(), now, "upload"));
        conversionEnqueuer.enqueue(documentId);
        return document;
    }

    /**
     * Re-run conversion → AI classification → indexing for an already-ingested
     * document. Resets its status to RECEIVED (so the UI reflects the requeue)
     * and re-enqueues the durable job, atomically. Recovers a FAILED document
     * or re-classifies one whose earlier extraction was skipped/incomplete.
     */
    @Transactional
    public void reprocess(String documentId, String userId) {
        long now = Instant.now().toEpochMilli();
        statuses.findById(documentId).ifPresent(status -> {
            status.transition(DocumentState.RECEIVED, userId, now);
            statuses.save(status);
        });
        conversionEnqueuer.reprocess(documentId);
    }
}
