package de.dms.documents.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.documents.control.Ingestion;
import de.dms.documents.entity.Document;
import de.dms.documents.entity.DocumentRepository;
import de.dms.documents.entity.DocumentState;
import de.dms.documents.entity.DocumentStatus;
import de.dms.documents.entity.DocumentStatusRepository;
import de.dms.documents.entity.Rendition;
import de.dms.documents.entity.RenditionRepository;
import de.dms.documents.entity.RenditionType;
import de.dms.crosscutting.platform.objectstore.control.ObjectStore;
import de.dms.crosscutting.platform.control.NotFoundException;
import de.dms.crosscutting.platform.control.Paging;
import de.dms.crosscutting.platform.control.SqlJson;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentsController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final Ingestion ingestion;
    private final DocumentsFacade facade;
    private final DocumentRepository documents;
    private final DocumentStatusRepository statuses;
    private final RenditionRepository renditions;
    private final ObjectStore objectStore;

    public DocumentsController(CurrentUser currentUser, Authorization authorization, Ingestion ingestion,
                               DocumentsFacade facade, DocumentRepository documents,
                               DocumentStatusRepository statuses, RenditionRepository renditions,
                               ObjectStore objectStore) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.ingestion = ingestion;
        this.facade = facade;
        this.documents = documents;
        this.statuses = statuses;
        this.renditions = renditions;
        this.objectStore = objectStore;
    }

    public record RenditionDto(String type, String mimeType, long sizeBytes, String checksumSha256,
                               String producer) {
    }

    public record DocumentDto(String id, String name, String orgUnitId, String status,
                              String ingestDate, List<RenditionDto> renditions) {
    }

    @PostMapping
    public ResponseEntity<DocumentDto> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam("orgUnitId") String orgUnitId) {
        UserRef user = currentUser.require();
        String documentId = UUID.randomUUID().toString();
        // authorization first (audited), then binary, then the atomic DB commit
        authorization.requireWrite(user, ResourceRef.document(documentId, orgUnitId));
        String filename = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
        Ingestion.StoredBinary binary;
        try (InputStream content = file.getInputStream()) {
            binary = ingestion.store(documentId, filename, file.getContentType(), content, file.getSize());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Document document = facade.registerUpload(documentId, filename, user.id(), orgUnitId, binary);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(document));
    }

    @GetMapping
    public List<DocumentDto> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size) {
        UserRef user = currentUser.require();
        List<String> visible = authorization.visibleOrgUnitIds(user);
        if (visible.isEmpty()) {
            return List.of();
        }
        List<Document> pageDocs = documents.findRecentByOrgUnits(SqlJson.array(visible),
                Paging.size(size), Paging.offset(page, size));
        return toDtos(pageDocs);
    }

    @PostMapping("/{id}/reprocess")
    public DocumentDto reprocess(@PathVariable String id) {
        UserRef user = currentUser.require();
        Document document = documents.findById(id).orElseThrow(() -> new NotFoundException("document " + id));
        // write authorization, so 403≡404 hides existence from callers who may not see it
        authorization.requireWrite(user, ResourceRef.document(id, document.getOrgUnitId()));
        facade.reprocess(id, user.id());
        return toDto(document);
    }

    @GetMapping("/{id}")
    public DocumentDto get(@PathVariable String id) {
        UserRef user = currentUser.require();
        Document document = documents.findById(id).orElseThrow(() -> new NotFoundException("document " + id));
        authorization.requireRead(user, ResourceRef.document(id, document.getOrgUnitId()));
        return toDto(document);
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable String id,
                                         @RequestParam(defaultValue = "PDF_A") RenditionType type) {
        UserRef user = currentUser.require();
        Document document = documents.findById(id).orElseThrow(() -> new NotFoundException("document " + id));
        authorization.requireRead(user, ResourceRef.document(id, document.getOrgUnitId()));
        Rendition rendition = renditions.findByDocumentIdAndType(id, type)
                .or(() -> renditions.findByDocumentIdAndType(id, RenditionType.ORIGINAL))
                .orElseThrow(() -> new NotFoundException("rendition"));
        // streamed, not buffered; integrity is pinned by the digest verified on
        // write (S-3), so downloads no longer re-hash the whole file
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(rendition.getMimeType()))
                .contentLength(rendition.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.getName(), StandardCharsets.UTF_8).build().toString())
                .body(new InputStreamResource(objectStore.stream(rendition.getStorageKey())));
    }

    private DocumentDto toDto(Document document) {
        return toDtos(List.of(document)).get(0);
    }

    /** One page in three queries — statuses and renditions are batch-fetched, not read per row. */
    private List<DocumentDto> toDtos(List<Document> pageDocs) {
        List<String> ids = pageDocs.stream().map(Document::getId).toList();
        Map<String, DocumentStatus> statusById = statuses.findAllById(ids).stream()
                .collect(Collectors.toMap(DocumentStatus::getDocumentId, Function.identity()));
        Map<String, List<Rendition>> renditionsById = renditions.findByDocumentIdIn(ids).stream()
                .collect(Collectors.groupingBy(Rendition::getDocumentId));
        return pageDocs.stream()
                .map(document -> toDto(document,
                        statusById.get(document.getId()),
                        renditionsById.getOrDefault(document.getId(), List.of())))
                .toList();
    }

    private DocumentDto toDto(Document document, DocumentStatus status, List<Rendition> documentRenditions) {
        String state = status == null ? DocumentState.RECEIVED.name() : status.getStatus().name();
        List<RenditionDto> renditionDtos = documentRenditions.stream()
                .map(r -> new RenditionDto(r.getType().name(), r.getMimeType(), r.getSizeBytes(),
                        r.getChecksumSha256(), r.getProducer()))
                .toList();
        return new DocumentDto(document.getId(), document.getName(), document.getOrgUnitId(), state,
                Instant.ofEpochMilli(document.getIngestDate()).toString(), renditionDtos);
    }
}
