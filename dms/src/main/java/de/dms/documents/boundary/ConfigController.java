package de.dms.documents.boundary;

import de.dms.documents.control.ControlledVocabulary;
import de.dms.crosscutting.platform.control.DmsProperties;
import de.dms.crosscutting.security.control.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Serves deployment configuration the UI needs: vocabulary + AI status. */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final CurrentUser currentUser;
    private final ControlledVocabulary vocabulary;
    private final boolean aiEnabled;

    public ConfigController(CurrentUser currentUser, ControlledVocabulary vocabulary, DmsProperties properties) {
        this.currentUser = currentUser;
        this.vocabulary = vocabulary;
        this.aiEnabled = properties.services() != null && properties.services().extraction() != null
                && properties.services().extraction().configured();
    }

    public record ConfigDto(List<String> documentClasses, boolean aiEnabled) {
    }

    @GetMapping
    public ConfigDto get() {
        currentUser.require();
        return new ConfigDto(vocabulary.documentClasses(), aiEnabled);
    }
}
