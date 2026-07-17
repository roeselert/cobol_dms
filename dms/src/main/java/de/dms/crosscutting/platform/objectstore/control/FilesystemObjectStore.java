package de.dms.crosscutting.platform.objectstore.control;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

/** Local-disk binding used when no bucket is configured (development/demo). */
public class FilesystemObjectStore implements ObjectStore {

    private final Path baseDir;

    public FilesystemObjectStore(Path baseDir) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
    }

    @Override
    public void put(String key, byte[] bytes) {
        try {
            Path target = resolve(key);
            createParentDirs(target);
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot write " + key, e);
        }
    }

    @Override
    public void put(String key, InputStream in, long sizeBytes) {
        Path target = resolve(key);
        try {
            Path parent = createParentDirs(target);
            // spool next to the target, move on success — an aborted upload
            // never leaves a partial object under the real key
            Path partial = Files.createTempFile(parent, ".part-", null);
            long copied;
            try (OutputStream out = Files.newOutputStream(partial)) {
                copied = in.transferTo(out);
            }
            if (copied != sizeBytes) {
                Files.deleteIfExists(partial);
                throw new StorageUnavailableException(
                        "short write for " + key + ": got " + copied + " of " + sizeBytes + " bytes");
            }
            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot write " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot read " + key, e);
        }
    }

    @Override
    public InputStream stream(String key) {
        try {
            return Files.newInputStream(resolve(key));
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot read " + key, e);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public List<String> list(String keyPrefix) {
        Path dir = resolve(keyPrefix);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(Files::isRegularFile)
                    .map(file -> keyPrefix + file.getFileName())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot list " + keyPrefix, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageUnavailableException("cannot delete " + key, e);
        }
    }

    @Override
    public void verifyReachable() {
        try {
            Files.createDirectories(baseDir);
        } catch (IOException e) {
            throw new StorageUnavailableException("object store directory unavailable", e);
        }
    }

    /** Ensures the target's parent directory exists and returns it (never null — keys resolve under baseDir). */
    private Path createParentDirs(Path target) throws IOException {
        Path parent = target.getParent();
        if (parent == null) {
            parent = baseDir;
        }
        Files.createDirectories(parent);
        return parent;
    }

    private Path resolve(String key) {
        Path resolved = baseDir.resolve(key).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("invalid storage key");
        }
        return resolved;
    }
}
