package de.dms.crosscutting.platform.control;

/** 403 — insufficient role on a resource the caller is allowed to see. */
public class ForbiddenException extends DomainException {

    public ForbiddenException(String message) {
        super(message);
    }
}
