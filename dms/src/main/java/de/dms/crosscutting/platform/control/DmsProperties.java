package de.dms.crosscutting.platform.control;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Externalized configuration (12-factor). Secrets arrive via environment
 * variables / Space Secrets, are read once at startup and never logged (S-2).
 */
@ConfigurationProperties(prefix = "dms")
public record DmsProperties(
        String dataDir,
        Security security,
        Upload upload,
        Worker worker,
        Bucket bucket,
        Services services,
        Feed feed,
        Backup backup) {

    public record Security(String mode, List<String> bootstrapAdminEmails) {
        public boolean devMode() {
            return "dev".equalsIgnoreCase(mode);
        }
    }

    public record Upload(long maxBytes) {
    }

    /** {@code batchSize} is the number of jobs drained sequentially per poll round. */
    public record Worker(boolean enabled, long pollMillis, int batchSize, int maxAttempts,
                         long backoffBaseMillis, long leaseSeconds) {
    }

    public record Bucket(String endpoint, String name, String region, String keyId, String secret) {
        public boolean configured() {
            return endpoint != null && !endpoint.isBlank();
        }
    }

    /**
     * The two Python document services (conversion = PDF/A + OCR + text,
     * extraction = AI metadata), called synchronously by the ingest worker
     * with a shared bearer token. What to extract (document classes, intents,
     * fields, Ordnungsbegriff types) lives in the database catalogs and
     * travels with every extraction request; how to reach the AI provider is
     * the extraction service's own configuration.
     */
    public record Services(String token, Endpoint conversion, Endpoint extraction) {

        public record Endpoint(String url, long connectTimeoutSeconds, long readTimeoutSeconds) {
            public boolean configured() {
                return url != null && !url.isBlank();
            }
        }
    }

    public record Feed(String tokenSecret, long tokenTtlDays) {
    }

    public record Backup(boolean enabled, long intervalMinutes, int retentionCount) {
    }
}
