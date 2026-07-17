package de.dms.crosscutting.platform.control;

import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;

/**
 * Hourly consistent SQLite snapshot (VACUUM INTO) pushed to the object store
 * — RPO ≤ 1 h (R-3, §10.6).
 *
 * VACUUM cannot run inside a transaction, so this uses the raw JDBC
 * connection (autocommit) instead of a repository {@code @Query} — the one
 * statement in the codebase that cannot live in a Spring Data repository.
 */
@Service
public class Backup {

    private static final Logger LOGGER = LoggerFactory.getLogger(Backup.class);
    private static final String REMOTE_PREFIX = "backups/";

    private final DataSource dataSource;
    private final ObjectStore objectStore;
    private final Path backupDir;
    private final int retentionCount;

    public Backup(DataSource dataSource, ObjectStore objectStore, DmsProperties properties) {
        this.dataSource = dataSource;
        this.objectStore = objectStore;
        this.backupDir = Path.of(properties.dataDir(), "backups");
        this.retentionCount = properties.backup().retentionCount();
    }

    public void safeRun() {
        try {
            run();
        } catch (Exception e) {
            LOGGER.error("backup failed", e);
        }
    }

    public void run() throws Exception {
        Files.createDirectories(backupDir);
        String name = "dms-" + Instant.now().toEpochMilli() + ".db";
        Path snapshot = backupDir.resolve(name);
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, snapshot.toAbsolutePath().toString());
            statement.executeUpdate();
        }
        try (var content = Files.newInputStream(snapshot)) {
            objectStore.put(REMOTE_PREFIX + name, content, Files.size(snapshot));
        }
        LOGGER.info("backup {} uploaded", name);
        rotateLocal();
        rotateRemote();
    }

    /** Keep only the three newest local snapshots (the bucket holds the history). */
    private void rotateLocal() throws Exception {
        List<Path> snapshots;
        try (var files = Files.list(backupDir)) {
            snapshots = files.filter(p -> p.getFileName().toString().startsWith("dms-"))
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());
        }
        for (int i = 0; i < snapshots.size() - 3; i++) {
            Files.deleteIfExists(snapshots.get(i));
        }
    }

    /**
     * Bounded remote history (dms.backup.retention-count newest snapshots).
     * The epoch-millis in the name make the lexicographic order chronological.
     */
    private void rotateRemote() {
        List<String> remote = objectStore.list(REMOTE_PREFIX);
        for (int i = 0; i < remote.size() - retentionCount; i++) {
            objectStore.delete(remote.get(i));
        }
    }
}
