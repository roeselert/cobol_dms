package de.dms.crosscutting.platform.objectstore.control;

import java.io.InputStream;
import java.util.List;

/**
 * Binary storage behind a stable BC boundary (M-1): the iteration-1 binding
 * (filesystem or HF Bucket via the S3 API) is the only thing that changes
 * when the store moves. Large payloads (uploads, downloads, backups) use the
 * streaming variants so a 100 MB document never has to sit on the heap.
 */
public interface ObjectStore {

    void put(String key, byte[] bytes);

    /** Streams exactly {@code sizeBytes} from {@code in}; fails without leaving a partial object visible. */
    void put(String key, InputStream in, long sizeBytes);

    byte[] get(String key);

    /** The object's bytes as a stream; the caller closes it. */
    InputStream stream(String key);

    boolean exists(String key);

    /** Keys under the given prefix (a "directory" ending in '/'), lexicographically sorted. */
    List<String> list(String keyPrefix);

    void delete(String key);

    /** Throws when the store is unreachable — used by the readiness probe. */
    void verifyReachable();
}
