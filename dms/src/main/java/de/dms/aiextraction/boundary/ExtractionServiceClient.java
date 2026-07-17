package de.dms.aiextraction.boundary;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.aiextraction.control.IntentCatalog;
import de.dms.aiextraction.control.OrdnungsbegriffCatalog;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.documents.control.ControlledVocabulary;
import de.dms.documents.control.MetadataSuggestions;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client for the Python extraction service. The catalogs — document classes,
 * intents with their fields, active Ordnungsbegriff types — are read from the
 * database per call and travel in the request payload, so admin edits take
 * effect on the next extraction without a restart; prompt assembly and
 * response parsing live in the service. The AI provider connection is the
 * service's own configuration — this side only knows the service URL.
 *
 * <p>An extraction service that is reachable but has no AI provider token
 * answers {@code status=unconfigured} (a 200): that maps to
 * {@link UnconfiguredException} and ends as a graceful skip, never as retry
 * or circuit-breaker accounting.
 */
@Component
public class ExtractionServiceClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ControlledVocabulary vocabulary;
    private final IntentCatalog intentCatalog;
    private final OrdnungsbegriffCatalog ordnungsbegriffCatalog;
    private final boolean configured;

    public ExtractionServiceClient(DmsProperties properties, ObjectMapper objectMapper,
                                   ControlledVocabulary vocabulary, IntentCatalog intentCatalog,
                                   OrdnungsbegriffCatalog ordnungsbegriffCatalog) {
        this.objectMapper = objectMapper;
        this.vocabulary = vocabulary;
        this.intentCatalog = intentCatalog;
        this.ordnungsbegriffCatalog = ordnungsbegriffCatalog;
        DmsProperties.Services services = properties.services();
        DmsProperties.Services.Endpoint endpoint = services == null ? null : services.extraction();
        this.configured = endpoint != null && endpoint.configured();
        this.restClient = configured ? buildClient(services) : null;
    }

    private static RestClient buildClient(DmsProperties.Services services) {
        DmsProperties.Services.Endpoint endpoint = services.extraction();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(endpoint.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(endpoint.readTimeoutSeconds()));
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(endpoint.url().endsWith("/")
                        ? endpoint.url().substring(0, endpoint.url().length() - 1)
                        : endpoint.url());
        if (services.token() != null && !services.token().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + services.token());
        }
        return builder.build();
    }

    /** The extraction service is reachable but its AI provider is not configured. */
    public static class UnconfiguredException extends RuntimeException {
        public UnconfiguredException() {
            super("extraction service has no AI provider configured");
        }
    }

    /** No extraction service URL is configured at all — suggestions are skipped. */
    public boolean isConfigured() {
        return configured;
    }

    public MetadataSuggestions extract(String filename, byte[] pdfa, String text) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("filename", filename);
        request.put("mimeType", "application/pdf");
        request.put("text", text == null ? "" : text);
        request.put("pdfBase64", Base64.getEncoder().encodeToString(pdfa));
        request.put("catalogs", catalogs());
        String responseBody = restClient.post()
                .uri("/extract")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(String.class);
        return parseResponse(responseBody);
    }

    /** The prompt-relevant projection of the three database catalogs. */
    private Map<String, Object> catalogs() {
        List<Map<String, Object>> classes = vocabulary.classes().stream()
                .map(c -> Map.<String, Object>of("name", c.getName(), "description", c.getDescription()))
                .toList();
        List<Map<String, Object>> intents = intentCatalog.allWithFields().stream()
                .map(intent -> Map.<String, Object>of(
                        "name", intent.intent().getName(),
                        "description", intent.intent().getDescription(),
                        "fields", intent.fields().stream()
                                .map(f -> Map.of("name", f.getName(), "description", f.getDescription()))
                                .toList()))
                .toList();
        List<Map<String, Object>> types = ordnungsbegriffCatalog.activeTypes().stream()
                .map(t -> Map.<String, Object>of("name", t.getName(), "description", t.getDescription()))
                .toList();
        return Map.of("documentClasses", classes, "intents", intents, "ordnungsbegriffTypes", types);
    }

    /**
     * Straight JSON-to-record mapping — all parsing leniency lives in the
     * service. The three-valued ordnungsbegriffe contract survives the wire:
     * JSON null maps to Java null (review), an array to entries/empty.
     * Package-private for unit testing.
     */
    MetadataSuggestions parseResponse(String responseBody) {
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (Exception e) {
            throw new IllegalStateException("cannot parse extraction service response", e);
        }
        String status = root.path("status").asText("");
        if ("unconfigured".equals(status)) {
            throw new UnconfiguredException();
        }
        if (!"ok".equals(status)) {
            throw new IllegalStateException("unexpected extraction service status: " + status);
        }
        JsonNode suggestions = root.path("suggestions");
        Map<String, String> additional = new LinkedHashMap<>();
        suggestions.path("additional").fields().forEachRemaining(entry -> {
            if (!entry.getValue().isNull()) {
                additional.put(entry.getKey(), entry.getValue().asText());
            }
        });
        return new MetadataSuggestions(
                textOrNull(suggestions, "documentDate"),
                textOrNull(suggestions, "documentClass"),
                textOrNull(suggestions, "filePlanReference"),
                additional,
                parseOrdnungsbegriffe(suggestions.get("ordnungsbegriffe")));
    }

    private static List<MetadataSuggestions.Ordnungsbegriff> parseOrdnungsbegriffe(JsonNode node) {
        if (node == null || node.isNull()) {
            return null; // malformed section service-side -> flag for review
        }
        List<MetadataSuggestions.Ordnungsbegriff> result = new ArrayList<>();
        for (JsonNode entry : node) {
            result.add(new MetadataSuggestions.Ordnungsbegriff(
                    entry.path("type").asText(), entry.path("value").asText()));
        }
        return List.copyOf(result);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
