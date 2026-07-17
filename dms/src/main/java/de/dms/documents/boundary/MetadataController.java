package de.dms.documents.boundary;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentRepository;
import de.dms.documents.control.MetadataValidation;
import de.dms.documents.entity.DocumentFilePlanReferenceRepository;
import de.dms.documents.entity.DocumentIntent;
import de.dms.documents.entity.DocumentIntentRepository;
import de.dms.documents.entity.DocumentMetadataRepository;
import de.dms.documents.entity.DocumentOrdnungsbegriff;
import de.dms.documents.entity.DocumentOrdnungsbegriffRepository;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documents/{documentId}/metadata")
public class MetadataController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final DocumentRepository documents;
    private final MetadataFacade facade;
    private final DocumentMetadataRepository metadata;
    private final DocumentFilePlanReferenceRepository filePlanReferences;
    private final DocumentOrdnungsbegriffRepository ordnungsbegriffe;
    private final DocumentIntentRepository intents;
    private final ObjectMapper objectMapper;

    public MetadataController(CurrentUser currentUser, Authorization authorization,
                              DocumentRepository documents, MetadataFacade facade,
                              DocumentMetadataRepository metadata,
                              DocumentFilePlanReferenceRepository filePlanReferences,
                              DocumentOrdnungsbegriffRepository ordnungsbegriffe,
                              DocumentIntentRepository intents, ObjectMapper objectMapper) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.documents = documents;
        this.facade = facade;
        this.metadata = metadata;
        this.filePlanReferences = filePlanReferences;
        this.ordnungsbegriffe = ordnungsbegriffe;
        this.intents = intents;
        this.objectMapper = objectMapper;
    }

    public record OrdnungsbegriffDto(String typeName, String value) {
    }

    public record IntentDto(String name, Map<String, String> fields) {
    }

    public record MetadataDto(String documentDate, String documentClass, String filePlanReference,
                              String akteId, boolean extractedByAi, int version,
                              List<OrdnungsbegriffDto> ordnungsbegriffe, String indexingFlag,
                              IntentDto intent) {
    }

    @PutMapping
    public MetadataDto save(@PathVariable String documentId,
                            @RequestBody MetadataValidation.MetadataInput input) {
        UserRef user = currentUser.require();
        Document document = requireDocument(documentId);
        authorization.requireWrite(user, ResourceRef.document(documentId, document.getOrgUnitId()));
        facade.save(document, input, user.id());
        return get(documentId);
    }

    @GetMapping
    public MetadataDto get(@PathVariable String documentId) {
        UserRef user = currentUser.require();
        Document document = requireDocument(documentId);
        authorization.requireRead(user, ResourceRef.document(documentId, document.getOrgUnitId()));
        var meta = metadata.findById(documentId).orElse(null);
        var reference = filePlanReferences.findById(documentId).orElse(null);
        var intent = intents.findById(documentId).orElse(null);
        List<DocumentOrdnungsbegriff> extracted =
                ordnungsbegriffe.findByDocumentIdOrderByTypeNameAscValueAsc(documentId);
        if (meta == null && reference == null && extracted.isEmpty() && intent == null) {
            throw new NotFoundException("no metadata for document " + documentId);
        }
        return new MetadataDto(
                meta == null ? null : meta.getDocumentDate(),
                meta == null ? null : meta.getDocumentClass(),
                reference == null ? null : reference.getFilePlanReference(),
                reference == null ? null : reference.getAkteId(),
                (meta != null && meta.isExtractedByAi()) || (reference != null && reference.isExtractedByAi()),
                meta == null ? 0 : meta.getVersion(),
                extracted.stream()
                        .map(entry -> new OrdnungsbegriffDto(entry.getTypeName(), entry.getValue()))
                        .toList(),
                meta == null || meta.getIndexingFlag() == null ? null : meta.getIndexingFlag().name(),
                toIntentDto(intent));
    }

    private IntentDto toIntentDto(DocumentIntent intent) {
        if (intent == null) {
            return null;
        }
        Map<String, String> fields = Map.of();
        if (intent.getFieldsJson() != null) {
            try {
                fields = objectMapper.readValue(intent.getFieldsJson(),
                        new TypeReference<Map<String, String>>() { });
            } catch (Exception e) {
                throw new IllegalStateException("cannot parse stored intent fields", e);
            }
        }
        return new IntentDto(intent.getName(), fields);
    }

    private Document requireDocument(String documentId) {
        return documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document " + documentId));
    }
}
