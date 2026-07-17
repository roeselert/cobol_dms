package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentOrdnungsbegriffRepository extends JpaRepository<DocumentOrdnungsbegriff, String> {

    List<DocumentOrdnungsbegriff> findByDocumentIdOrderByTypeNameAscValueAsc(String documentId);

    boolean existsByDocumentId(String documentId);
}
