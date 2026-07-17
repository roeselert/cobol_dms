package de.dms.documents.control;

import de.dms.documents.entity.Akte;
import de.dms.documents.entity.AkteRepository;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.SqlJson;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Find-or-create on the UNIQUE file_plan_reference: never a second Akte for
 * the same Ordnungsbegriff, naturally idempotent under reprocessing (US-08).
 */
@Service
public class Aktenbildung {

    private final AkteRepository akten;

    public Aktenbildung(AkteRepository akten) {
        this.akten = akten;
    }

    public Akte findOrCreate(String filePlanReference, String orgUnitId) {
        return akten.findByFilePlanReference(filePlanReference).orElseGet(() -> {
            try {
                return akten.save(new Akte(UUID.randomUUID().toString(), filePlanReference, orgUnitId,
                        Instant.now().toEpochMilli()));
            } catch (DataIntegrityViolationException e) {
                // lost the race — the unique constraint guarantees a single Akte
                return akten.findByFilePlanReference(filePlanReference)
                        .orElseThrow(() -> e);
            }
        });
    }

    public Akte require(String id) {
        return akten.findById(id).orElseThrow(() -> new NotFoundException("akte " + id));
    }

    public List<Akte> visibleAkten(List<String> visibleOrgUnitIds) {
        return visibleOrgUnitIds.isEmpty() ? List.of() : akten.findByOrgUnits(SqlJson.array(visibleOrgUnitIds));
    }

    public record AkteDocument(String documentId, String name, long ingestDate, String status) {
    }

    /**
     * Paginated document list of an Akte, ordered by ingest date. The
     * caller's visibility predicate is part of the query (ACL push-down).
     */
    public List<AkteDocument> documentsOf(String akteId, List<String> visibleOrgUnitIds, int page, int size) {
        if (visibleOrgUnitIds.isEmpty()) {
            return List.of();
        }
        return akten.findAkteDocuments(akteId, SqlJson.array(visibleOrgUnitIds), size, page * size).stream()
                .map(row -> new AkteDocument(row.getDocumentId(), row.getName(),
                        row.getIngestDate(), row.getStatus()))
                .toList();
    }
}
