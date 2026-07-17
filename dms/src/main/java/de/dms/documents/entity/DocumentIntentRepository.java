package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface DocumentIntentRepository extends JpaRepository<DocumentIntent, String> {

    List<DocumentIntent> findByDocumentIdIn(Collection<String> documentIds);
}
