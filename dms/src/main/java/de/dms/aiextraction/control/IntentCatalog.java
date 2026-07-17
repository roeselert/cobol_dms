package de.dms.aiextraction.control;

import de.dms.aiextraction.entity.ExtractionIntent;
import de.dms.aiextraction.entity.ExtractionIntentField;
import de.dms.aiextraction.entity.ExtractionIntentFieldRepository;
import de.dms.aiextraction.entity.ExtractionIntentRepository;
import de.dms.crosscutting.platform.control.ConflictException;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.UnprocessableException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The catalog of extraction intents and their fields, backed by the
 * extraction_intent(_field) tables. The AI system prompt lists every intent
 * with its fields; the model picks the single best match and extracts that
 * intent's fields. Saving an intent always replaces its full field list —
 * no per-field endpoints, which keeps the API and the admin UI simple.
 */
@Service
public class IntentCatalog {

    /** Field names become JSON keys in the model's answer — keep them key-shaped. */
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    /** Keys the extraction wire contract already claims for the typed metadata slots. */
    private static final Set<String> RESERVED_KEYS = Set.of(
            "documentDate", "documentClass", "filePlanReference", "intent",
            "extractedText", "ordnungsbegriffe");

    private final ExtractionIntentRepository intents;
    private final ExtractionIntentFieldRepository fields;

    public IntentCatalog(ExtractionIntentRepository intents, ExtractionIntentFieldRepository fields) {
        this.intents = intents;
        this.fields = fields;
    }

    public record FieldInput(String name, String description) {
    }

    public record IntentWithFields(ExtractionIntent intent, List<ExtractionIntentField> fields) {
    }

    public List<IntentWithFields> allWithFields() {
        return intents.findAllByOrderByNameAsc().stream()
                .map(intent -> new IntentWithFields(intent, fields.findByIntentIdOrderByNameAsc(intent.getId())))
                .toList();
    }

    /** Union of all intents' field names — what the response parser looks for. */
    public List<String> allFieldNames() {
        Set<String> names = new LinkedHashSet<>();
        for (IntentWithFields intent : allWithFields()) {
            intent.fields().forEach(field -> names.add(field.getName()));
        }
        return List.copyOf(names);
    }

    @Transactional
    public IntentWithFields create(String name, String description, List<FieldInput> fieldInputs) {
        String intentName = requireText(name, "intent name");
        if (intents.existsByName(intentName)) {
            throw new ConflictException("intent already exists: " + intentName);
        }
        ExtractionIntent intent = intents.save(new ExtractionIntent(UUID.randomUUID().toString(),
                intentName, requireText(description, "intent description"), Instant.now().toEpochMilli()));
        return new IntentWithFields(intent, replaceFields(intent.getId(), fieldInputs));
    }

    @Transactional
    public IntentWithFields update(String id, String name, String description, List<FieldInput> fieldInputs) {
        ExtractionIntent intent = require(id);
        String intentName = requireText(name, "intent name");
        if (!intentName.equals(intent.getName()) && intents.existsByName(intentName)) {
            throw new ConflictException("intent already exists: " + intentName);
        }
        intent.rename(intentName, requireText(description, "intent description"));
        intent = intents.save(intent);
        return new IntentWithFields(intent, replaceFields(id, fieldInputs));
    }

    @Transactional
    public void delete(String id) {
        ExtractionIntent intent = require(id);
        fields.deleteByIntentId(intent.getId());
        intents.delete(intent);
    }

    private List<ExtractionIntentField> replaceFields(String intentId, List<FieldInput> fieldInputs) {
        fields.deleteByIntentId(intentId);
        Set<String> seen = new LinkedHashSet<>();
        for (FieldInput input : fieldInputs == null ? List.<FieldInput>of() : fieldInputs) {
            String fieldName = requireText(input.name(), "field name").trim();
            if (!FIELD_NAME.matcher(fieldName).matches()) {
                throw new UnprocessableException("field name must be a JSON key (letters, digits, _): " + fieldName);
            }
            if (RESERVED_KEYS.contains(fieldName)) {
                throw new UnprocessableException("field name is reserved: " + fieldName);
            }
            if (!seen.add(fieldName)) {
                throw new UnprocessableException("duplicate field name: " + fieldName);
            }
            fields.save(new ExtractionIntentField(UUID.randomUUID().toString(), intentId,
                    fieldName, requireText(input.description(), "field description")));
        }
        return fields.findByIntentIdOrderByNameAsc(intentId);
    }

    private ExtractionIntent require(String id) {
        return intents.findById(id).orElseThrow(() -> new NotFoundException("intent " + id));
    }

    private static String requireText(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new UnprocessableException(what + " is required");
        }
        return value.trim();
    }
}
