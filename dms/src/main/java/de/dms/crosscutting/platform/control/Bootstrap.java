package de.dms.crosscutting.platform.control;

import de.dms.conversion.control.JobDispatcher;
import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Startup sequence (§7.5): Flyway has already migrated (Boot auto-config);
 * this verifies the object store for readiness, prepares the data dirs and
 * launches the in-process worker loops on the TaskScheduler.
 */
@Component
public class Bootstrap implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bootstrap.class);

    private final DmsProperties properties;
    private final ObjectStore objectStore;
    private final JobDispatcher jobDispatcher;
    private final Backup backup;
    private final TaskScheduler taskScheduler;

    public Bootstrap(DmsProperties properties, ObjectStore objectStore, JobDispatcher jobDispatcher,
                     Backup backup, TaskScheduler taskScheduler) {
        this.properties = properties;
        this.objectStore = objectStore;
        this.jobDispatcher = jobDispatcher;
        this.backup = backup;
        this.taskScheduler = taskScheduler;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        prepareDataDirs();
        try {
            objectStore.verifyReachable();
            LOGGER.info("object store reachable");
        } catch (Exception e) {
            // not fatal: uploads will 503 until the store recovers; readiness reports it
            LOGGER.warn("object store not reachable at startup: {}", e.getMessage());
        }

        if (properties.worker().enabled()) {
            taskScheduler.scheduleWithFixedDelay(this::safePoll,
                    Duration.ofMillis(properties.worker().pollMillis()));
            taskScheduler.scheduleWithFixedDelay(jobDispatcher::sweepExpiredLeases, Duration.ofSeconds(60));
            LOGGER.info("ingest worker started (poll every {} ms)", properties.worker().pollMillis());
        }
        if (properties.backup().enabled()) {
            taskScheduler.scheduleWithFixedDelay(backup::safeRun,
                    Duration.ofMinutes(properties.backup().intervalMinutes()));
            LOGGER.info("backup scheduled every {} minutes", properties.backup().intervalMinutes());
        }
    }

    private void safePoll() {
        try {
            jobDispatcher.poll();
        } catch (Exception e) {
            LOGGER.error("worker poll failed", e);
        }
    }

    private void prepareDataDirs() {
        try {
            Path dataDir = Path.of(properties.dataDir());
            Files.createDirectories(dataDir.resolve("tmp"));
            Files.createDirectories(dataDir.resolve("backups"));
        } catch (IOException e) {
            throw new IllegalStateException("cannot prepare data directory " + properties.dataDir(), e);
        }
    }
}
