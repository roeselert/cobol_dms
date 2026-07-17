package de.dms.documents.control;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentClassRepository;
import de.dms.documents.entity.DocumentFilePlanReferenceRepository;
import de.dms.documents.entity.DocumentIntent;
import de.dms.documents.entity.DocumentIntentRepository;
import de.dms.documents.entity.DocumentMetadata;
import de.dms.documents.entity.DocumentMetadataRepository;
import de.dms.documents.entity.DocumentOrdnungsbegriff;
import de.dms.documents.entity.DocumentOrdnungsbegriffRepository;
import de.dms.documents.entity.IndexingFlag;
import de.dms.documents.search.control.SearchIndexer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataValidationTest {

    private final DocumentMetadataRepository metadata = mock(DocumentMetadataRepository.class);
    private final DocumentFilePlanReferenceRepository references = mock(DocumentFilePlanReferenceRepository.class);
    private final DocumentOrdnungsbegriffRepository ordnungsbegriffe =
            mock(DocumentOrdnungsbegriffRepository.class);
    private final DocumentIntentRepository intents = mock(DocumentIntentRepository.class);
    private final MetadataValidation validation = new MetadataValidation(metadata, references, ordnungsbegriffe,
            intents, mock(Aktenbildung.class), mock(SearchIndexer.class),
            new ControlledVocabulary(mock(DocumentClassRepository.class)), new ObjectMapper());

    private final Document document = new Document("doc-1", "rechnung.pdf", "alice", "org-1", 0L);

    private MetadataSuggestions suggestions(List<MetadataSuggestions.Ordnungsbegriff> extracted) {
        return new MetadataSuggestions("2026-07-01", null, null, Map.of(), extracted);
    }

    private DocumentMetadata savedMetadata() {
        ArgumentCaptor<DocumentMetadata> captor = ArgumentCaptor.forClass(DocumentMetadata.class);
        verify(metadata).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void extractedOrdnungsbegriffeAreStoredDedupedWithoutFlag() {
        MetadataSuggestions.Ordnungsbegriff kunde = new MetadataSuggestions.Ordnungsbegriff(
                "Kundennummer", "7000123456");
        validation.applySuggestions(document, suggestions(List.of(kunde, kunde,
                new MetadataSuggestions.Ordnungsbegriff("Vertragsnummer", "V-9"))));

        assertThat(savedMetadata().getIndexingFlag()).isNull();
        verify(ordnungsbegriffe, times(2)).save(any(DocumentOrdnungsbegriff.class));
    }

    @Test
    void noOrdnungsbegriffFoundFlagsManualIndexing() {
        validation.applySuggestions(document, suggestions(List.of()));
        assertThat(savedMetadata().getIndexingFlag()).isEqualTo(IndexingFlag.MANUAL_INDEXING);
        verify(ordnungsbegriffe, never()).save(any());
    }

    @Test
    void malformedOrdnungsbegriffeSectionFlagsReviewButKeepsClassification() {
        validation.applySuggestions(document, suggestions(null));
        DocumentMetadata saved = savedMetadata();
        assertThat(saved.getIndexingFlag()).isEqualTo(IndexingFlag.REVIEW);
        assertThat(saved.getDocumentDate()).isEqualTo("2026-07-01");
    }

    @Test
    void flagForIndexingCreatesFlagOnlyRowWhenNoMetadataExists() {
        validation.flagForIndexing(document, IndexingFlag.REVIEW);
        DocumentMetadata saved = savedMetadata();
        assertThat(saved.getIndexingFlag()).isEqualTo(IndexingFlag.REVIEW);
        assertThat(saved.getVersion()).isZero();
        assertThat(saved.getDocumentClass()).isNull();
    }

    @Test
    void flagForIndexingNeverTouchesUserConfirmedMetadata() {
        DocumentMetadata confirmed = new DocumentMetadata("doc-1", "2026-07-01", "RECHNUNG",
                false, "alice", 1, 0L);
        when(metadata.findById("doc-1")).thenReturn(Optional.of(confirmed));

        validation.flagForIndexing(document, IndexingFlag.MANUAL_INDEXING);

        assertThat(confirmed.getIndexingFlag()).isNull();
        verify(metadata, never()).save(any());
    }

    @Test
    void detectedIntentIsStoredWithItsFieldValues() {
        validation.applySuggestions(document, new MetadataSuggestions("2026-07-01", null, null,
                Map.of("intent", "Rechnungseingang", "rechnungsnummer", "R-1"), List.of()));

        ArgumentCaptor<DocumentIntent> captor = ArgumentCaptor.forClass(DocumentIntent.class);
        verify(intents).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Rechnungseingang");
        assertThat(captor.getValue().getFieldsJson()).contains("\"rechnungsnummer\":\"R-1\"");
    }

    @Test
    void reprocessWithoutIntentRemovesTheStoredIntent() {
        DocumentIntent stored = new DocumentIntent("doc-1", "Rechnungseingang", null, 0L);
        when(intents.findById("doc-1")).thenReturn(Optional.of(stored));

        validation.applySuggestions(document, suggestions(List.of()));

        verify(intents).delete(stored);
        verify(intents, never()).save(any());
    }

    @Test
    void userConfirmedUpdateClearsTheFlag() {
        DocumentMetadata prefill = new DocumentMetadata("doc-1", null, null, true, null, 0, 0L);
        prefill.flagForIndexing(IndexingFlag.MANUAL_INDEXING);
        prefill.update("2026-07-01", "RECHNUNG", false, "alice", 1L);
        assertThat(prefill.getIndexingFlag()).isNull();
    }
}
