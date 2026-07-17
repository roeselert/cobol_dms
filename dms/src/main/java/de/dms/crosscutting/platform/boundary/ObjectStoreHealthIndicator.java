package de.dms.crosscutting.platform.boundary;

import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness contribution: is the bucket / object store reachable? (R-2, §10.5)
 * Actuator derives the contributor name "objectStore" from the class name.
 */
@Component
public class ObjectStoreHealthIndicator implements HealthIndicator {

    private final ObjectStore objectStore;

    public ObjectStoreHealthIndicator(ObjectStore objectStore) {
        this.objectStore = objectStore;
    }

    @Override
    public Health health() {
        try {
            objectStore.verifyReachable();
            return Health.up().build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", e.getMessage()).build();
        }
    }
}
