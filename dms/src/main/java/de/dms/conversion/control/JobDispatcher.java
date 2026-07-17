package de.dms.conversion.control;

import de.dms.aiextraction.control.MetadataExtraction;
import de.dms.conversion.boundary.ConversionServiceClient;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import de.dms.documents.control.MetadataSuggestions;
import de.dms.documents.control.MetadataValidation;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentRepository;
import de.dms.documents.entity.DocumentState;
import de.dms.documents.entity.DocumentStatusRepository;
import de.dms.documents.entity.IndexingFlag;
import de.dms.documents.entity.Rendition;
import de.dms.documents.entity.RenditionRepository;
import de.dms.documents.entity.RenditionType;
import de.dms.documents.search.control.SearchIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * In-process ingest worker (§7.5): claims jobs from the durable queue, drives
 * conversion → AI extraction → indexing via the two document services, and
 * applies retry/backoff with a terminal FAILED after N attempts (R-1).
 * Reprocessing is idempotent — the PDF/A and text renditions use
 * deterministic storage keys, so a re-run overwrites rather than duplicates
 * (R-2). Depends only on control layers and HTTP clients, never on Spring
 * MVC.
 *
 * <p>The conversion service returns the PDF/A and its plain text in one
 * response; that deterministic OCR text is stored in the bucket (the search
 * index is a derived cache rebuilt from the bucket, and this container has
 * no PDF toolchain of its own) and feeds the index even when AI extraction
 * is skipped or failing.
 */
@Service
public class JobDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobDispatcher.class);

    private final JobQueue jobQueue;
    private final ConversionServiceClient conversionClient;
    private final MetadataExtraction metadataExtraction;
    private final MetadataValidation metadataValidation;
    private final SearchIndexer searchIndexer;
    private final ObjectStore objectStore;
    private final DocumentRepository documents;
    private final DocumentStatusRepository statuses;
    private final RenditionRepository renditions;
    private final DmsProperties.Worker config;

    private volatile long lastPollAt;

    public JobDispatcher(JobQueue jobQueue, ConversionServiceClient conversionClient,
                         MetadataExtraction metadataExtraction, MetadataValidation metadataValidation,
                         SearchIndexer searchIndexer, ObjectStore objectStore,
                         DocumentRepository documents, DocumentStatusRepository statuses,
                         RenditionRepository renditions, DmsProperties properties) {
        this.jobQueue = jobQueue;
        this.conversionClient = conversionClient;
        this.metadataExtraction = metadataExtraction;
        this.metadataValidation = metadataValidation;
        this.searchIndexer = searchIndexer;
        this.objectStore = objectStore;
        this.documents = documents;
        this.statuses = statuses;
        this.renditions = renditions;
        this.config = properties.worker();
    }

    /** One poll round: drain up to the configured batch of jobs, sequentially. */
    public void poll() {
        heartbeat();
        for (int i = 0; i < Math.max(1, config.batchSize()); i++) {
            Optional<JobQueue.ClaimedJob> claimed = jobQueue.claim(config.leaseSeconds());
            if (claimed.isEmpty()) {
                return;
            }
            // per-job heartbeat: the readiness probe must not count a long,
            // legitimate conversion (bounded by the lease) as a dead worker
            heartbeat();
            process(claimed.get());
        }
    }

    private void heartbeat() {
        lastPollAt = Instant.now().toEpochMilli();
    }

    /** Re-queues jobs whose lease expired while RUNNING (crashed worker, R-2). */
    public void sweepExpiredLeases() {
        int requeued = jobQueue.requeueExpiredLeases();
        if (requeued > 0) {
            LOGGER.info("re-queued {} jobs with expired leases", requeued);
        }
    }

    public long getLastPollAt() {
        return lastPollAt;
    }

    private void process(JobQueue.ClaimedJob job) {
        try {
            Document document = documents.findById(job.documentId())
                    .orElseThrow(() -> new IllegalStateException("job references missing document " + job.documentId()));
            transition(document.getId(), DocumentState.CONVERTING);

            Rendition original = renditions.findByDocumentIdAndType(document.getId(), RenditionType.ORIGINAL)
                    .orElseThrow(() -> new IllegalStateException("no ORIGINAL rendition for " + document.getId()));
            byte[] originalBytes = objectStore.get(original.getStorageKey());

            ConversionServiceClient.ConversionResult result =
                    conversionClient.convert(document.getName(), original.getMimeType(), originalBytes);
            if ("passthrough".equals(result.producer())) {
                LOGGER.warn("document {} stored WITHOUT PDF/A normalization (conversion service has "
                        + "no toolchain); rendition marked producer=passthrough", document.getId());
            }
            String pdfaKey = "renditions/" + document.getId() + "/pdfa.pdf"; // deterministic -> idempotent re-run
            objectStore.put(pdfaKey, result.pdfa()); // binary before metadata
            upsertRendition(document.getId(), RenditionType.PDF_A, pdfaKey, "application/pdf",
                    result.pdfa(), result.producer());

            // the OCR text goes into the bucket too: the index is rebuilt from
            // the bucket, and text extraction now lives in the conversion service
            byte[] textBytes = result.text().getBytes(StandardCharsets.UTF_8);
            String textKey = "renditions/" + document.getId() + "/text.txt";
            objectStore.put(textKey, textBytes);
            upsertRendition(document.getId(), RenditionType.TEXT, textKey, "text/plain; charset=utf-8",
                    textBytes, result.producer());

            // AI is optional: on failure suggestions are skipped, document still READY —
            // but flagged for review (errored) or manual indexing (unconfigured).
            // The PDF/A rendition and its text are sent (not the original) so the
            // model always sees a PDF.
            MetadataExtraction.Outcome outcome =
                    metadataExtraction.suggest(document.getName(), result.pdfa(), result.text());
            outcome.suggestions().ifPresentOrElse(
                    s -> metadataValidation.applySuggestions(document, s),
                    () -> metadataValidation.flagForIndexing(document,
                            outcome.failed() ? IndexingFlag.REVIEW : IndexingFlag.MANUAL_INDEXING));
            searchIndexer.index(document.getId(), indexText(result.text(), outcome));

            transition(document.getId(), DocumentState.READY);
            jobQueue.markDone(job.id());
            LOGGER.info("document {} READY (attempt {})", document.getId(), job.attempts());
        } catch (Exception e) {
            LOGGER.warn("conversion attempt {} failed for document {}: {}", job.attempts(), job.documentId(),
                    e.getMessage());
            boolean willRetry = jobQueue.retryOrFail(job.id(), job.attempts(), config.maxAttempts(),
                    config.backoffBaseMillis(), e.getMessage());
            if (!willRetry) {
                transition(job.documentId(), DocumentState.FAILED);
            }
        }
    }

    /**
     * The deterministic OCR text plus, when extraction succeeded, the
     * additional fields and Ordnungsbegriffe — all searchable. Unlike the
     * model-echoed text of earlier iterations this is available even when AI
     * extraction is skipped or failing.
     */
    private static String indexText(String conversionText, MetadataExtraction.Outcome outcome) {
        StringBuilder text = new StringBuilder(conversionText == null ? "" : conversionText);
        Optional<MetadataSuggestions> suggestions = outcome.suggestions();
        if (suggestions.isPresent()) {
            if (suggestions.get().additional() != null) {
                suggestions.get().additional().forEach((key, value) ->
                        text.append('\n').append(key).append(": ").append(value));
            }
            if (suggestions.get().ordnungsbegriffe() != null) {
                suggestions.get().ordnungsbegriffe().forEach(entry ->
                        text.append('\n').append(entry.type()).append(": ").append(entry.value()));
            }
        }
        return text.toString();
    }

    private void transition(String documentId, DocumentState state) {
        statuses.findById(documentId).ifPresent(status -> {
            status.transition(state, null, Instant.now().toEpochMilli());
            statuses.save(status);
        });
    }

    private void upsertRendition(String documentId, RenditionType type, String key, String mimeType,
                                 byte[] content, String producer) {
        renditions.findByDocumentIdAndType(documentId, type)
                .ifPresent(renditions::delete);
        renditions.save(new Rendition(UUID.randomUUID().toString(), documentId, type,
                key, mimeType, content.length, de.dms.documents.control.Ingestion.sha256(content),
                Instant.now().toEpochMilli(), producer));
    }
}
