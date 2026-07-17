package de.dms.crosscutting.platform.control;

/**
 * 404 — used both for genuinely missing resources and for read/visibility
 * denials (uniform 403≡404 existence-hiding, S-1).
 */
public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }
}
