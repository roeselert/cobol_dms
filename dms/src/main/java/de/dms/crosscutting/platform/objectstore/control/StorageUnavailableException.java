package de.dms.crosscutting.platform.objectstore.control;

/** 503 — object store unreachable; upload rejected without orphaned metadata (R-1). */
public class StorageUnavailableException extends RuntimeException {

    public StorageUnavailableException(String message) {
        super(message);
    }

    public StorageUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
