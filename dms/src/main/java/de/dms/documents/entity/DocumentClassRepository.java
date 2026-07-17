package de.dms.documents.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentClassRepository extends JpaRepository<DocumentClass, String> {

    List<DocumentClass> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
