package de.dms.conversion.control;

import de.dms.conversion.entity.ConversionJob;
import de.dms.conversion.entity.ConversionJobRepository;
import de.dms.conversion.entity.JobState;
import de.dms.crosscutting.platform.control.SqlJson;
import de.dms.documents.control.ConversionEnqueuer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The relational DB is the queue (R-1). SQLite has no SKIP LOCKED, but with
 * the single in-process consumer (§7.5) a serialized claim — SELECT the next
 * eligible row, then a guarded UPDATE — is sufficient and correct.
 */
@Service
public class JobQueue implements ConversionEnqueuer {

    private final ConversionJobRepository jobs;

    public JobQueue(ConversionJobRepository jobs) {
        this.jobs = jobs;
    }

    /** Enqueue inside the caller's transaction — same commit as the Document insert (R-1). */
    @Override
    public void enqueue(String documentId) {
        jobs.save(new ConversionJob(UUID.randomUUID().toString(), documentId, now()));
    }

    /**
     * Re-run the pipeline for an already-ingested document (manual retry). The
     * document's existing job row is reset to a fresh set of attempts so the
     * worker picks it up again; if the row is somehow missing a new one is
     * enqueued. Re-running is safe: conversion writes a deterministic PDF/A key.
     */
    @Override
    @Transactional
    public void reprocess(String documentId) {
        jobs.findByDocumentId(documentId).ifPresentOrElse(
                job -> jobs.resetForRetry(job.getId(), now()),
                () -> enqueue(documentId));
    }

    public record ClaimedJob(String id, String documentId, int attempts) {
    }

    /** One queue row for the job-queue view, named after its document. */
    public record JobListEntry(String id, String documentId, String documentName, JobState status,
                               int attempts, String lastError, long createdAt) {
    }

    /**
     * One page of the jobs whose documents sit in the given org units, active
     * work first (QUEUED, RUNNING, DONE, FAILED — the enum's declaration
     * order), then by document name. Sorted here because the status column
     * stores enum names, so SQL ordering would be alphabetical.
     */
    public List<JobListEntry> list(List<String> orgUnitIds, int page, int size) {
        List<JobListEntry> sorted = jobs.findAllWithDocument(SqlJson.array(orgUnitIds)).stream()
                .map(row -> new JobListEntry(row.getId(), row.getDocumentId(), row.getDocumentName(),
                        JobState.valueOf(row.getStatus()), row.getAttempts(), row.getLastError(),
                        row.getCreatedAt()))
                .sorted(Comparator.comparing(JobListEntry::status)
                        .thenComparing(JobListEntry::documentName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        int from = Math.min(page * size, sorted.size());
        return sorted.subList(from, Math.min(from + size, sorted.size()));
    }

    /** Atomic claim with a lease for crash recovery (R-2). */
    public Optional<ClaimedJob> claim(long leaseSeconds) {
        return jobs.findNextQueued(now()).flatMap(job -> {
            int updated = jobs.claim(job.getId(), now() + leaseSeconds * 1000);
            if (updated != 1) {
                return Optional.empty(); // guarded UPDATE lost the race
            }
            return Optional.of(new ClaimedJob(job.getId(), job.getDocumentId(), job.getAttempts() + 1));
        });
    }

    public void markDone(String jobId) {
        jobs.markDone(jobId);
    }

    /** Retry with backoff, or terminal FAILED once attempts are exhausted (R-1). */
    public boolean retryOrFail(String jobId, int attempts, int maxAttempts, long backoffBaseMillis, String error) {
        String truncated = error == null ? "unknown error" : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            jobs.markFailed(jobId, truncated);
            return false;
        }
        long backoff = backoffBaseMillis * (1L << Math.min(attempts, 16));
        jobs.requeue(jobId, truncated, now() + backoff);
        return true;
    }

    /** Re-queue jobs whose lease expired while RUNNING (crashed worker, R-2). */
    public int requeueExpiredLeases() {
        return jobs.requeueExpiredLeases(now());
    }

    private long now() {
        return Instant.now().toEpochMilli();
    }
}
