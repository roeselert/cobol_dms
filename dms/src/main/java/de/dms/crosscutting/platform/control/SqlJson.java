package de.dms.crosscutting.platform.control;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Renders a bind parameter for SQLite's {@code json_each()} table function:
 * the visibility list travels as one bound JSON string instead of an
 * {@code IN (...)} expansion, so it can never exceed SQLite's bound-variable
 * limit no matter how many org units a user sees.
 */
public interface SqlJson {

    ObjectMapper MAPPER = new ObjectMapper();

    static String array(List<String> values) {
        try {
            return MAPPER.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("cannot render JSON array parameter", e);
        }
    }
}
