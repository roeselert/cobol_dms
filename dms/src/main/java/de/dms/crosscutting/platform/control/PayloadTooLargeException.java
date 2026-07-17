package de.dms.crosscutting.platform.control;

/** 413 — upload exceeds the configured size limit. */
public class PayloadTooLargeException extends DomainException {

    public PayloadTooLargeException(String message) {
        super(message);
    }
}
