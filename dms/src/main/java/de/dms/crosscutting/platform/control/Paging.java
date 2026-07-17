package de.dms.crosscutting.platform.control;

/**
 * Boundary-level clamping for paging parameters. SQLite treats a negative
 * LIMIT as "unlimited", so every list endpoint must clamp before the values
 * reach the SQL (P-1): {@code page >= 0}, {@code 1 <= size <= 100}.
 */
public interface Paging {

    int MAX_SIZE = 100;

    static int page(int page) {
        return Math.max(page, 0);
    }

    static int size(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    static int offset(int page, int size) {
        return page(page) * size(size);
    }
}
