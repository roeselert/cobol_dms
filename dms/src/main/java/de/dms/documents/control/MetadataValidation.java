package de.dms.documents.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.documents.entity.Akte;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentFilePlanReference;
import de.dms.documents.entity.DocumentFilePlanReferenceRepository;
import de.dms.documents.entity.DocumentIntent;
import de.dms.documents.entity.DocumentIntentRepository;
import de.dms.documents.entity.DocumentMetadata;
import de.dms.documents.entity.DocumentMetadataRepository;
import de.dms.documents.entity.DocumentOrdnungsbegriff;
import de.dms.documents.entity.DocumentOrdnungsbegriffRepository;
import de.dms.documents.entity.IndexingFlag;
import de.dms.crosscutting.platform.control.UnprocessableException;
import de.dms.documents.search.control.SearchIndexer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Validates mandatory fields and the configured controlled vocabulary,
 * versions every change, and triggers Aktenbildung on confirmed saves
 * (US-03 → US-08).
 */
@Service
public class MetadataValidation {

    /** The key the extraction wire contract reserves for the detected intent's name. */
    private static final String INTENT_KEY = "intent";

    private final DocumentMetadataRepository metadata;
    private final DocumentFilePlanReferenceRepository filePlanReferences;
    private final DocumentOrdnungsbegriffRepository ordnungsbegriffe;
    private final DocumentIntentRepository intents;
    private final Aktenbildung aktenbildung;
    private final SearchIndexer searchIndexer;
    private final ControlledVocabulary vocabulary;
    private final ObjectMapper objectMapper;

    public MetadataValidation(DocumentMetadataRepository metadata,
                              DocumentFilePlanReferenceRepository filePlanReferences,
                              DocumentOrdnungsbegriffRepository ordnungsbegriffe,
                              DocumentIntentRepository intents,
                              Aktenbildung aktenbildung, SearchIndexer searchIndexer,
                              ControlledVocabulary vocabulary, ObjectMapper objectMapper) {
        this.metadata = metadata;
        this.filePlanReferences = filePlanReferences;
        this.ordnungsbegriffe = ordnungsbegriffe;
        this.intents = intents;
        this.aktenbildung = aktenbildung;
        this.searchIndexer = searchIndexer;
        this.vocabulary = vocabulary;
        this.objectMapper = objectMapper;
    }

    public record MetadataInput(String documentDate, String documentClass, String filePlanReference) {
    }

    /** User-confirmed save: validate everything, version, form the Akte. */
    public void save(Document document, MetadataInput input, String userId) {
        String documentDate = requireDate(input.documentDate());
        String documentClass = requireClass(input.documentClass());
        if (input.filePlanReference() == null || input.filePlanReference().isBlank()) {
            throw new UnprocessableException("filePlanReference (Ordnungsbegriff) is required");
        }
        String reference = input.filePlanReference().trim();
        long now = Instant.now().toEpochMilli();

        Akte akte = aktenbildung.findOrCreate(reference, document.getOrgUnitId());

        metadata.findById(document.getId()).ifPresentOrElse(
                existing -> {
                    existing.update(documentDate, documentClass, false, userId, now);
                    metadata.save(existing);
                },
                () -> metadata.save(new DocumentMetadata(document.getId(), documentDate, documentClass,
                        false, userId, 1, now)));

        filePlanReferences.findById(document.getId()).ifPresentOrElse(
                existing -> {
                    existing.update(reference, akte.getId(), false, userId);
                    filePlanReferences.save(existing);
                },
                () -> {
                    DocumentFilePlanReference created = new DocumentFilePlanReference(
                            document.getId(), reference, false, userId, 1, now);
                    created.linkAkte(akte.getId());
                    filePlanReferences.save(created);
                });

        searchIndexer.index(document.getId(), null);
    }

    /** AI suggestions prefill empty metadata only — never overwrite user input, never form Akten. */
    public void applySuggestions(Document document, MetadataSuggestions suggestions) {
        long now = Instant.now().toEpochMilli();
        IndexingFlag flag = indexingFlag(suggestions.ordnungsbegriffe());
        if (metadata.findById(document.getId()).isEmpty()) {
            String date = parseDateOrNull(suggestions.documentDate());
            String documentClass = vocabulary.normalize(suggestions.documentClass()).orElse(null);
            if (date != null || documentClass != null || flag != null) {
                DocumentMetadata created = new DocumentMetadata(
                        document.getId(), date, documentClass, true, null, 0, now);
                created.flagForIndexing(flag);
                metadata.save(created);
            }
        }
        if (suggestions.ordnungsbegriffe() != null && !suggestions.ordnungsbegriffe().isEmpty()
                && !ordnungsbegriffe.existsByDocumentId(document.getId())) {
            Set<MetadataSuggestions.Ordnungsbegriff> unique = new LinkedHashSet<>(suggestions.ordnungsbegriffe());
            for (MetadataSuggestions.Ordnungsbegriff entry : unique) {
                ordnungsbegriffe.save(new DocumentOrdnungsbegriff(UUID.randomUUID().toString(),
                        document.getId(), entry.type(), entry.value(), true, now));
            }
        }
        if (filePlanReferences.findById(document.getId()).isEmpty()
                && suggestions.filePlanReference() != null && !suggestions.filePlanReference().isBlank()) {
            filePlanReferences.save(new DocumentFilePlanReference(
                    document.getId(), suggestions.filePlanReference().trim(), true, null, 0, now));
        }
        applyIntent(document, suggestions.additional(), now);
    }

    /**
     * The detected intent is AI-only data (no user input to protect), so a
     * reprocess replaces the stored row with the latest extraction — or
     * removes it when the new answer carries no intent.
     */
    private void applyIntent(Document document, Map<String, String> additional, long now) {
        intents.findById(document.getId()).ifPresent(intents::delete);
        String name = additional == null ? null : additional.get(INTENT_KEY);
        if (name == null || name.isBlank()) {
            return;
        }
        Map<String, String> fields = new LinkedHashMap<>(additional);
        fields.remove(INTENT_KEY);
        intents.save(new DocumentIntent(document.getId(), name.trim(), toJsonOrNull(fields), now));
    }

    private String toJsonOrNull(Map<String, String> fields) {
        if (fields.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot serialize intent fields", e);
        }
    }

    /**
     * Marks a document for human indexing when there are no AI suggestions at
     * all (extraction failed or AI unconfigured). No metadata row is a valid
     * state, so the flag rides on a version-0 prefill row; user-confirmed
     * rows are never touched.
     */
    public void flagForIndexing(Document document, IndexingFlag flag) {
        long now = Instant.now().toEpochMilli();
        metadata.findById(document.getId()).ifPresentOrElse(
                existing -> {
                    if (existing.getVersion() == 0 && existing.getIndexingFlag() == null) {
                        existing.flagForIndexing(flag);
                        metadata.save(existing);
                    }
                },
                () -> {
                    DocumentMetadata created = new DocumentMetadata(
                            document.getId(), null, null, false, null, 0, now);
                    created.flagForIndexing(flag);
                    metadata.save(created);
                });
    }

    /** No Ordnungsbegriff found → manual indexing; malformed section (null) → review. */
    private static IndexingFlag indexingFlag(List<MetadataSuggestions.Ordnungsbegriff> extracted) {
        if (extracted == null) {
            return IndexingFlag.REVIEW;
        }
        return extracted.isEmpty() ? IndexingFlag.MANUAL_INDEXING : null;
    }

    private static String requireDate(String value) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableException("documentDate is required (yyyy-MM-dd)");
        }
        String date = parseDateOrNull(value);
        if (date == null) {
            throw new UnprocessableException("documentDate must be an ISO date (yyyy-MM-dd)");
        }
        return date;
    }

    private String requireClass(String value) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableException("documentClass is required");
        }
        return vocabulary.normalize(value).orElseThrow(() ->
                new UnprocessableException("documentClass outside controlled vocabulary: "
                        + vocabulary.documentClasses()));
    }

    private static String parseDateOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
