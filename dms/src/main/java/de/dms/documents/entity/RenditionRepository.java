package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RenditionRepository extends JpaRepository<Rendition, String> {

    List<Rendition> findByDocumentId(String documentId);

    List<Rendition> findByDocumentIdIn(List<String> documentIds);

    Optional<Rendition> findByDocumentIdAndType(String documentId, RenditionType type);
}
