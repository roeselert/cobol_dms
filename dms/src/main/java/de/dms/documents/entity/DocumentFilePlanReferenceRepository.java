package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentFilePlanReferenceRepository extends JpaRepository<DocumentFilePlanReference, String> {

    List<DocumentFilePlanReference> findByAkteId(String akteId);
}
