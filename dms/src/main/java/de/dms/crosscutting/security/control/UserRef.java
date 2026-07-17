package de.dms.crosscutting.security.control;

/** The authenticated caller, resolved once per request. */
public record UserRef(String id, String email, boolean bootstrapAdmin) {
}
