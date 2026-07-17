package de.dms.documents.control;

import de.dms.documents.entity.DocumentClass;
import de.dms.documents.entity.DocumentClassRepository;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * The controlled vocabulary for document classification, backed by the
 * document_class table instead of a compiled enum or static config, so the
 * class set (and the descriptions the AI prompt uses) can be managed at
 * runtime; validation (422 on values outside the vocabulary, US-03) and the
 * AI system prompt both draw from here. Reads hit the table per call so
 * admin edits take effect immediately.
 */
@Service
public class ControlledVocabulary {

    private final DocumentClassRepository repository;

    public ControlledVocabulary(DocumentClassRepository repository) {
        this.repository = repository;
    }

    /** All classes including descriptions, for the AI prompt and the admin UI. */
    public List<DocumentClass> classes() {
        return repository.findAllByOrderByNameAsc();
    }

    /** The class names (upper-case codes) for validation messages and the UI selects. */
    public List<String> documentClasses() {
        return classes().stream().map(DocumentClass::getName).toList();
    }

    /** Case-insensitive match against the vocabulary; empty when outside it. */
    public Optional<String> normalize(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String candidate = value.trim().toUpperCase(Locale.ROOT);
        return repository.existsByName(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    @Transactional
    public DocumentClass create(String name, String description) {
        String code = requireName(name);
        if (repository.existsByName(code)) {
            throw new ConflictException("document class already exists: " + code);
        }
        return repository.save(new DocumentClass(
                UUID.randomUUID().toString(), code, requireDescription(description), Instant.now().toEpochMilli()));
    }

    @Transactional
    public DocumentClass update(String id, String name, String description) {
        DocumentClass documentClass = require(id);
        String code = requireName(name);
        if (!code.equals(documentClass.getName()) && repository.existsByName(code)) {
            throw new ConflictException("document class already exists: " + code);
        }
        documentClass.rename(code, requireDescription(description));
        return repository.save(documentClass);
    }

    /**
     * Removes a class from the vocabulary. Existing document_metadata rows keep
     * their stored value — validation is write-time only, history stays readable.
     */
    @Transactional
    public void delete(String id) {
        repository.delete(require(id));
    }

    private DocumentClass require(String id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("document class " + id));
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new UnprocessableException("document class name is required");
        }
        return name.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new UnprocessableException("document class description is required");
        }
        return description.trim();
    }
}
