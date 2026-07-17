package de.dms.crosscutting.accesscontrol.control;

/** Ordered by privilege: VIEWER < EDITOR < ADMIN. */
public enum Role {
    VIEWER, EDITOR, ADMIN;

    public boolean atLeast(Role other) {
        return ordinal() >= other.ordinal();
    }
}
