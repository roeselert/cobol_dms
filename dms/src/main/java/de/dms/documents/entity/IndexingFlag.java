package de.dms.documents.entity;

/**
 * Why a document still needs human indexing: MANUAL_INDEXING when the AI
 * found no Ordnungsbegriff (or was not configured), REVIEW when extraction
 * errored. Cleared by the user-confirmed metadata save.
 */
public enum IndexingFlag {
    MANUAL_INDEXING,
    REVIEW
}
