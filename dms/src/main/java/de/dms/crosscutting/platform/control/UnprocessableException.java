package de.dms.crosscutting.platform.control;

/** 422 — missing mandatory metadata field / value outside controlled vocabulary. */
public class UnprocessableException extends DomainException {

    public UnprocessableException(String message) {
        super(message);
    }
}
