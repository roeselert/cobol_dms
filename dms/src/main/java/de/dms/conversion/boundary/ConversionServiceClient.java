package de.dms.conversion.boundary;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dms.crosscutting.platform.control.DmsProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Base64;

/**
 * Client for the Python conversion service: original bytes go out as
 * multipart, the PDF/A rendition and its plain text come back in one JSON
 * response. Every failure — service down, timeout, non-2xx — surfaces as
 * {@link ConversionFailedException}, which the dispatcher answers with
 * retry/backoff exactly as it did for a failed local toolchain (R-1).
 *
 * <p>Conversion is not optional: with the worker enabled the constructor
 * fails fast when no service URL is configured, so a misdeployment shows at
 * startup instead of as a poison job queue.
 */
@Component
public class ConversionServiceClient {

    private final RestClient restClient;
    private final boolean configured;
    private final boolean workerEnabled;

    public ConversionServiceClient(DmsProperties properties) {
        DmsProperties.Services services = properties.services();
        DmsProperties.Services.Endpoint endpoint = services == null ? null : services.conversion();
        this.configured = endpoint != null && endpoint.configured();
        this.workerEnabled = properties.worker() != null && properties.worker().enabled();
        this.restClient = configured ? buildClient(services) : null;
    }

    /** Startup check, outside the constructor (CT_CONSTRUCTOR_THROW). */
    @PostConstruct
    void verifyMandatoryConfiguration() {
        if (!configured && workerEnabled) {
            throw new IllegalStateException(
                    "the ingest worker is enabled but dms.services.conversion.url is not configured "
                            + "(set DMS_CONVERSION_URL or disable the worker)");
        }
    }

    private static RestClient buildClient(DmsProperties.Services services) {
        DmsProperties.Services.Endpoint endpoint = services.conversion();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(endpoint.connectTimeoutSeconds()));
        requestFactory.setReadTimeout(Duration.ofSeconds(endpoint.readTimeoutSeconds()));
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(trimTrailingSlash(endpoint.url()));
        if (services.token() != null && !services.token().isBlank()) {
            builder = builder.defaultHeader("Authorization", "Bearer " + services.token());
        }
        return builder.build();
    }

    public static class ConversionFailedException extends RuntimeException {
        public ConversionFailedException(String message, Throwable cause) {
            super(message, cause);
        }

        public ConversionFailedException(String message) {
            super(message);
        }
    }

    /**
     * The converted PDF/A plus its plain text, extracted service-side in one
     * pass. {@code producer} names the tool that made the bytes
     * (ocrmypdf | ghostscript | libreoffice | passthrough) — "passthrough"
     * means the service had no PDF/A toolchain and returned the original.
     */
    public record ConversionResult(byte[] pdfa, String text, String producer, boolean ocrApplied) {
    }

    public ConversionResult convert(String filename, String mimeType, byte[] original) {
        if (!configured) {
            throw new ConversionFailedException("conversion service is not configured");
        }
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("file", new ByteArrayResource(original), MediaType.APPLICATION_OCTET_STREAM)
                .filename(filename == null || filename.isBlank() ? "input" : filename);
        body.part("mimeType", mimeType == null ? "application/octet-stream" : mimeType);
        ConvertResponse response;
        try {
            response = restClient.post()
                    .uri("/convert")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body.build())
                    .retrieve()
                    .body(ConvertResponse.class);
        } catch (Exception e) {
            throw new ConversionFailedException("conversion service call failed for " + filename
                    + ": " + e.getMessage(), e);
        }
        if (response == null || response.pdfBase64() == null || response.pdfBase64().isBlank()) {
            throw new ConversionFailedException("conversion service returned no PDF for " + filename);
        }
        byte[] pdfa;
        try {
            pdfa = Base64.getDecoder().decode(response.pdfBase64());
        } catch (IllegalArgumentException e) {
            throw new ConversionFailedException("conversion service returned invalid base64 for "
                    + filename, e);
        }
        return new ConversionResult(pdfa,
                response.text() == null ? "" : response.text(),
                response.producer() == null ? "unknown" : response.producer(),
                Boolean.TRUE.equals(response.ocrApplied()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ConvertResponse(String pdfBase64, String text, String producer, Boolean ocrApplied) {
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
