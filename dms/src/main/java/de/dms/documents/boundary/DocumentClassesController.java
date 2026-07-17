package de.dms.documents.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.crosscutting.accesscontrol.entity.AuditAction;
import de.dms.documents.control.ControlledVocabulary;
import de.dms.documents.entity.DocumentClass;
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
 * CRUD for the controlled vocabulary. Readable by every authenticated user
 * (the vocabulary is not a secret — it drives the UI selects); mutations are
 * reserved for bootstrap admins because the catalog is deployment-global.
 */
@RestController
@RequestMapping("/api/v1/document-classes")
public class DocumentClassesController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final ControlledVocabulary vocabulary;

    public DocumentClassesController(CurrentUser currentUser, Authorization authorization,
                                     ControlledVocabulary vocabulary) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.vocabulary = vocabulary;
    }

    public record DocumentClassDto(String id, String name, String description) {
    }

    public record SaveRequest(String name, String description) {
    }

    @GetMapping
    public List<DocumentClassDto> list() {
        currentUser.require();
        return vocabulary.classes().stream().map(DocumentClassesController::toDto).toList();
    }

    @PostMapping
    public ResponseEntity<DocumentClassDto> create(@RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.documentClass(null));
        DocumentClass created = vocabulary.create(request.name(), request.description());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @PutMapping("/{id}")
    public DocumentClassDto update(@PathVariable String id, @RequestBody SaveRequest request) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.WRITE, ResourceRef.documentClass(id));
        return toDto(vocabulary.update(id, request.name(), request.description()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        UserRef user = currentUser.require();
        authorization.requireBootstrapAdmin(user, AuditAction.DELETE, ResourceRef.documentClass(id));
        vocabulary.delete(id);
        return ResponseEntity.noContent().build();
    }

    private static DocumentClassDto toDto(DocumentClass documentClass) {
        return new DocumentClassDto(documentClass.getId(), documentClass.getName(), documentClass.getDescription());
    }
}
