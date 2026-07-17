package de.dms.aiextraction.control;

import de.dms.aiextraction.boundary.ExtractionServiceClient;
import de.dms.documents.control.MetadataSuggestions;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Metadata suggestions with graceful degradation: when the extraction
 * service is down or unconfigured, suggestions are simply skipped and the
 * document still reaches READY (QG-1 trade-off, §5).
 */
@Service
public class MetadataExtraction {

    private static final Logger LOGGER = LoggerFactory.getLogger(MetadataExtraction.class);

    private final ExtractionServiceClient client;

    public MetadataExtraction(ExtractionServiceClient client) {
        this.client = client;
    }

    /**
     * How the extraction went: {@code failed} distinguishes an errored service
     * call (document flagged for review) from a skipped one — no extraction
     * service or no AI provider configured — where the document simply needs
     * manual indexing.
     */
    public record Outcome(Optional<MetadataSuggestions> suggestions, boolean failed) {

        public static Outcome success(MetadataSuggestions suggestions) {
            return new Outcome(Optional.of(suggestions), false);
        }

        public static Outcome skipped() {
            return new Outcome(Optional.empty(), false);
        }

        public static Outcome failure() {
            return new Outcome(Optional.empty(), true);
        }
    }

    @Retry(name = "ai")
    @CircuitBreaker(name = "ai", fallbackMethod = "skip")
    public Outcome suggest(String filename, byte[] pdfa, String extractedText) {
        if (!client.isConfigured()) {
            return Outcome.skipped();
        }
        try {
            return Outcome.success(client.extract(filename, pdfa, extractedText));
        } catch (ExtractionServiceClient.UnconfiguredException e) {
            // a 200 from the service, deliberately outside retry/breaker accounting
            LOGGER.info("extraction service has no AI provider configured, skipping suggestions for {}",
                    filename);
            return Outcome.skipped();
        }
    }

    @SuppressWarnings("unused") // resilience4j fallback
    private Outcome skip(String filename, byte[] pdfa, String extractedText, Throwable cause) {
        LOGGER.warn("AI extraction unavailable, skipping suggestions for {}: {}", filename, cause.getMessage());
        return Outcome.failure();
    }
}
