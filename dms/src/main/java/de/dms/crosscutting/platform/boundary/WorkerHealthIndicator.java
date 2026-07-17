package de.dms.crosscutting.platform.boundary;

import de.dms.conversion.control.JobDispatcher;
import de.dms.crosscutting.platform.control.DmsProperties;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Readiness contribution: is the in-process worker loop alive? (§10.5)
 * Actuator derives the contributor name "worker" from the class name.
 */
@Component
public class WorkerHealthIndicator implements HealthIndicator {

    private final JobDispatcher jobDispatcher;
    private final DmsProperties properties;

    public WorkerHealthIndicator(JobDispatcher jobDispatcher, DmsProperties properties) {
        this.jobDispatcher = jobDispatcher;
        this.properties = properties;
    }

    @Override
    public Health health() {
        if (!properties.worker().enabled()) {
            return Health.up().withDetail("worker", "disabled").build();
        }
        long lastPoll = jobDispatcher.getLastPollAt();
        // lease-aware staleness: the dispatcher heartbeats per job start, and a
        // single job may legitimately block for up to the lease duration (the
        // service read timeouts are bounded by it) — only silence beyond that
        // means a dead worker, not a slow conversion (M-6)
        long staleAfter = Math.max(properties.worker().pollMillis() * 5,
                properties.worker().leaseSeconds() * 1000 + 30_000);
        if (lastPoll == 0 || Instant.now().toEpochMilli() - lastPoll < staleAfter) {
            return Health.up().withDetail("lastPollAt", lastPoll).build();
        }
        return Health.down().withDetail("lastPollAt", lastPoll).build();
    }
}
