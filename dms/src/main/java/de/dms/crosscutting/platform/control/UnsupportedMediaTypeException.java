package de.dms.crosscutting.platform.control;

/** 415 — upload of a media type outside the accepted set. */
public class UnsupportedMediaTypeException extends DomainException {

    public UnsupportedMediaTypeException(String message) {
        super(message);
    }
}
