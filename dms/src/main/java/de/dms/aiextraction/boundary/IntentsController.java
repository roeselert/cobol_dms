package de.dms.aiextraction.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.crosscutting.accesscontrol.entity.AuditAction;
import de.dms.aiextraction.control.IntentCatalog;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CRUD for extraction intents. Saving an intent (POST/PUT) always carries the
 * full field list and replaces what is stored. Readable by every authenticated
 * user; mutations are reserved for bootstrap admins (deployment-global catalog).
 */
@RestController
@RequestMapping("/api/v1/intents")
public class IntentsController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final IntentCatalog intentCatalog;

    public IntentsController(CurrentUser currentUser, Authorization authorization, IntentCatalog intentCatalog) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.intentCatalog = intentCatalog;
    }

    public record FieldDto(String name, String description) {
    }

    public record IntentDto(String id, String name, String description, List<FieldDto> fields) {
    }

    public record SaveRequest(String name, String description, List<FieldDto> fields) {
    }

    @GetMapping
    public List<IntentDto> list() {
        currentUser.require();
        return intentCatalog.allWithFields().stream().map(IntentsController::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<IntentDto> create(@RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.intent(null));
        IntentCatalog.IntentWithFields created =
                intentCatalog.create(request.name(), request.description(), toInputs(request));
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public IntentDto update(@PathVariable String id, @RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.intent(id));
        return toDto(intentCatalog.update(id, request.name(), request.description(), toInputs(request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.DELETE, ResourceRef.intent(id));
        intentCatalog.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static List<IntentCatalog.FieldInput> toInputs(SaveRequest request) {
        return request.fields() == null ? List.of() : request.fields().stream()
                .map(field -> new IntentCatalog.FieldInput(field.name(), field.description()))
                .toList();
    }

    private static IntentDto toDto(IntentCatalog.IntentWithFields intent) {
        return new IntentDto(intent.intent().getId(), intent.intent().getName(), intent.intent().getDescription(),
                intent.fields().stream().map(field -> new FieldDto(field.getName(), field.getDescription())).toList());
    }
}
