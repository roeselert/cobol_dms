package de.dms.crosscutting.platform.control;

/** Base class for domain errors mapped to HTTP status codes at the boundary. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
