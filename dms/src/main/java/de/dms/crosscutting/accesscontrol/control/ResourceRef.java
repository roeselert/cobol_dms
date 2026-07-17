package de.dms.crosscutting.accesscontrol.control;

/** The resource an authorization decision is about. */
public record ResourceRef(String type, String id, String orgUnitId) {

    public static ResourceRef document(String id, String orgUnitId) {
        return new ResourceRef("DOCUMENT", id, orgUnitId);
    }

    public static ResourceRef akte(String id, String orgUnitId) {
        return new ResourceRef("AKTE", id, orgUnitId);
    }

    public static ResourceRef orgUnit(String id) {
        return new ResourceRef("ORG_UNIT", id, id);
    }

    /** Global catalog entries have no owning org unit. */
    public static ResourceRef documentClass(String id) {
        return new ResourceRef("DOCUMENT_CLASS", id, null);
    }

    public static ResourceRef intent(String id) {
        return new ResourceRef("EXTRACTION_INTENT", id, null);
    }

    public static ResourceRef ordnungsbegriffType(String id) {
        return new ResourceRef("ORDNUNGSBEGRIFF_TYPE", id, null);
    }
}
