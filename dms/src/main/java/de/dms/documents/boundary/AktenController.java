package de.dms.documents.boundary;

import de.dms.crosscutting.accesscontrol.control.Authorization;
import de.dms.crosscutting.accesscontrol.control.ResourceRef;
import de.dms.documents.control.Aktenbildung;
import de.dms.documents.entity.Akte;
import de.dms.crosscutting.platform.control.Paging;
import de.dms.crosscutting.security.control.CurrentUser;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/akten")
public class AktenController {

    private final CurrentUser currentUser;
    private final Authorization authorization;
    private final Aktenbildung aktenbildung;

    public AktenController(CurrentUser currentUser, Authorization authorization, Aktenbildung aktenbildung) {
        this.currentUser = currentUser;
        this.authorization = authorization;
        this.aktenbildung = aktenbildung;
    }

    public record AkteDto(String id, String filePlanReference, String orgUnitId) {
    }

    public record AkteDocumentDto(String documentId, String name, String ingestDate, String status) {
    }

    @GetMapping
    public List<AkteDto> list() {
        UserRef user = currentUser.require();
        return aktenbildung.visibleAkten(authorization.visibleOrgUnitIds(user)).stream()
                .map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public AkteDto get(@PathVariable String id) {
        UserRef user = currentUser.require();
        Akte akte = aktenbildung.require(id);
        // 403≡404: a forbidden akte is indistinguishable from a missing one (US-09)
        authorization.requireRead(user, ResourceRef.akte(id, akte.getOrgUnitId()));
        return toDto(akte);
    }

    @GetMapping("/{id}/documents")
    public List<AkteDocumentDto> documents(@PathVariable String id,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        UserRef user = currentUser.require();
        Akte akte = aktenbildung.require(id);
        authorization.requireRead(user, ResourceRef.akte(id, akte.getOrgUnitId()));
        return aktenbildung.documentsOf(id, authorization.visibleOrgUnitIds(user),
                        Paging.page(page), Paging.size(size)).stream()
                .map(doc -> new AkteDocumentDto(doc.documentId(), doc.name(),
                        Instant.ofEpochMilli(doc.ingestDate()).toString(), doc.status()))
                .toList();
    }

    private AkteDto toDto(Akte akte) {
        return new AkteDto(akte.getId(), akte.getFilePlanReference(), akte.getOrgUnitId());
    }
}
