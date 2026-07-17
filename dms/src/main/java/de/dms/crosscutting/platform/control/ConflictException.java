package de.dms.crosscutting.platform.control;

/** 409 — org cycle, delete of a non-empty unit, duplicate membership. */
public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }
}
