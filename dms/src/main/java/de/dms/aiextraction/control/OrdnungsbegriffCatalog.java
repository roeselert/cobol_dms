package de.dms.aiextraction.control;

import de.dms.aiextraction.entity.OrdnungsbegriffType;
import de.dms.aiextraction.entity.OrdnungsbegriffTypeRepository;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The catalog of Ordnungsbegriff types (business reference identifiers such
 * as Kundennummer), backed by the ordnungsbegriff_type table. The AI system
 * prompt lists every ACTIVE type with its description; the parser only keeps
 * extracted values whose type matches an active entry. Reads hit the table
 * per call, so admin edits take effect on the next extraction.
 */
@Service
public class OrdnungsbegriffCatalog {

    private final OrdnungsbegriffTypeRepository repository;

    public OrdnungsbegriffCatalog(OrdnungsbegriffTypeRepository repository) {
        this.repository = repository;
    }

    /** All types including inactive ones, for the admin UI. */
    public List<OrdnungsbegriffType> all() {
        return repository.findAllByOrderByNameAsc();
    }

    /** Active types only — what the AI prompt lists and the parser accepts. */
    public List<OrdnungsbegriffType> activeTypes() {
        return repository.findByActiveTrueOrderByNameAsc();
    }

    /** Case-insensitive match against the active types; the configured name wins. */
    public Optional<String> normalizeType(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String candidate = value.trim();
        return activeTypes().stream()
                .map(OrdnungsbegriffType::getName)
                .filter(name -> name.equalsIgnoreCase(candidate))
                .findFirst();
    }

    @Transactional
    public OrdnungsbegriffType create(String name, String description) {
        String typeName = requireText(name, "ordnungsbegriff type name");
        if (repository.existsByNameIgnoreCase(typeName)) {
            throw new ConflictException("ordnungsbegriff type already exists: " + typeName);
        }
        return repository.save(new OrdnungsbegriffType(UUID.randomUUID().toString(), typeName,
                requireText(description, "ordnungsbegriff type description"), true,
                Instant.now().toEpochMilli()));
    }

    @Transactional
    public OrdnungsbegriffType update(String id, String name, String description, boolean active) {
        OrdnungsbegriffType type = require(id);
        String typeName = requireText(name, "ordnungsbegriff type name");
        if (!typeName.equalsIgnoreCase(type.getName()) && repository.existsByNameIgnoreCase(typeName)) {
            throw new ConflictException("ordnungsbegriff type already exists: " + typeName);
        }
        type.update(typeName, requireText(description, "ordnungsbegriff type description"), active);
        return repository.save(type);
    }

    /**
     * Removes a type from the catalog. Existing document_ordnungsbegriff rows
     * keep their type_name snapshot — extraction stops for new documents only,
     * history stays readable.
     */
    @Transactional
    public void delete(String id) {
        repository.delete(require(id));
    }

    private OrdnungsbegriffType require(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("ordnungsbegriff type " + id));
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableException(what + " is required");
        }
        return value.trim();
    }
}
