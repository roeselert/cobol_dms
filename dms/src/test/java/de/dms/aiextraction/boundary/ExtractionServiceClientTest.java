package de.dms.aiextraction.boundary;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.FakeServices;
import de.dms.aiextraction.control.IntentCatalog;
import de.dms.aiextraction.control.OrdnungsbegriffCatalog;
import de.dms.aiextraction.entity.ExtractionIntent;
import de.dms.aiextraction.entity.ExtractionIntentField;
import de.dms.aiextraction.entity.ExtractionIntentFieldRepository;
import de.dms.aiextraction.entity.ExtractionIntentRepository;
import de.dms.aiextraction.entity.OrdnungsbegriffType;
import de.dms.aiextraction.entity.OrdnungsbegriffTypeRepository;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.documents.control.ControlledVocabulary;
import de.dms.documents.control.MetadataSuggestions;
import de.dms.documents.entity.DocumentClass;
import de.dms.documents.entity.DocumentClassRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExtractionServiceClientTest {

    private static FakeServices fake;

    @BeforeAll
    static void start() {
        fake = FakeServices.start();
    }

    @AfterAll
    static void stop() {
        fake.stop();
    }

    private ExtractionServiceClient client(String url) {
        DmsProperties properties = new DmsProperties(null, null, null, null, null,
                new DmsProperties.Services(FakeServices.TOKEN,
                        new DmsProperties.Services.Endpoint("", 10, 300),
                        new DmsProperties.Services.Endpoint(url, 10, 180)),
                null, null);

        DocumentClassRepository classes = mock(DocumentClassRepository.class);
        when(classes.findAllByOrderByNameAsc()).thenReturn(List.of(
                new DocumentClass("dc-1", "RECHNUNG", "Rechnungen", 0L)));

        ExtractionIntentRepository intents = mock(ExtractionIntentRepository.class);
        ExtractionIntentFieldRepository fields = mock(ExtractionIntentFieldRepository.class);
        when(intents.findAllByOrderByNameAsc()).thenReturn(List.of(
                new ExtractionIntent("in-1", "Rechnungseingang", "Eine Rechnung", 0L)));
        when(fields.findByIntentIdOrderByNameAsc("in-1")).thenReturn(List.of(
                new ExtractionIntentField("f-1", "in-1", "absender", "sender")));

        OrdnungsbegriffTypeRepository types = mock(OrdnungsbegriffTypeRepository.class);
        when(types.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(
                new OrdnungsbegriffType("ob-1", "Kundennummer", "Die Kundennummer", true, 0L)));

        return new ExtractionServiceClient(properties, new ObjectMapper(),
                new ControlledVocabulary(classes), new IntentCatalog(intents, fields),
                new OrdnungsbegriffCatalog(types));
    }

    @Test
    void unconfiguredUrlMeansNotConfigured() {
        assertThat(client("").isConfigured()).isFalse();
        assertThat(client(fake.baseUrl()).isConfigured()).isTrue();
    }

    @Test
    void unconfiguredServiceAnswerRaisesTheGracefulSkipSignal() {
        // the fake answers {"status":"unconfigured"} unless a response is scripted
        assertThatThrownBy(() -> client(fake.baseUrl()).extract("x.pdf", new byte[] {1}, "text"))
                .isInstanceOf(ExtractionServiceClient.UnconfiguredException.class);
    }

    @Test
    void mapsSuggestionsIncludingAdditionalAndOrdnungsbegriffe() {
        fake.enqueueExtractResponse("{\"status\":\"ok\",\"suggestions\":{"
                + "\"documentDate\":\"2026-07-01\",\"documentClass\":\"RECHNUNG\","
                + "\"filePlanReference\":null,"
                + "\"additional\":{\"intent\":\"Rechnungseingang\",\"absender\":\"Stadtwerke\"},"
                + "\"ordnungsbegriffe\":[{\"type\":\"Kundennummer\",\"value\":\"7000123456\"}]}}");
        MetadataSuggestions suggestions = client(fake.baseUrl()).extract("x.pdf", new byte[] {1}, "text");
        assertThat(suggestions.documentDate()).isEqualTo("2026-07-01");
        assertThat(suggestions.documentClass()).isEqualTo("RECHNUNG");
        assertThat(suggestions.filePlanReference()).isNull();
        assertThat(suggestions.additional()).containsExactlyInAnyOrderEntriesOf(
                Map.of("intent", "Rechnungseingang", "absender", "Stadtwerke"));
        assertThat(suggestions.ordnungsbegriffe()).containsExactly(
                new MetadataSuggestions.Ordnungsbegriff("Kundennummer", "7000123456"));
    }

    @Test
    void nullOrdnungsbegriffeSurviveTheWireAsNull() {
        fake.enqueueExtractResponse("{\"status\":\"ok\",\"suggestions\":{"
                + "\"documentClass\":\"RECHNUNG\",\"additional\":{},\"ordnungsbegriffe\":null}}");
        MetadataSuggestions suggestions = client(fake.baseUrl()).extract("x.pdf", new byte[] {1}, "text");
        assertThat(suggestions.ordnungsbegriffe()).isNull();
        assertThat(suggestions.documentClass()).isEqualTo("RECHNUNG");
    }

    @Test
    void emptyOrdnungsbegriffeStayEmpty() {
        fake.enqueueExtractResponse("{\"status\":\"ok\",\"suggestions\":{"
                + "\"documentClass\":\"RECHNUNG\",\"additional\":{},\"ordnungsbegriffe\":[]}}");
        assertThat(client(fake.baseUrl()).extract("x.pdf", new byte[] {1}, "text").ordnungsbegriffe())
                .isEmpty();
    }

    @Test
    void unreachableServiceRaisesForResilienceHandling() {
        assertThatThrownBy(() -> client("http://127.0.0.1:1").extract("x.pdf", new byte[] {1}, "text"))
                .isNotInstanceOf(ExtractionServiceClient.UnconfiguredException.class);
    }
}
