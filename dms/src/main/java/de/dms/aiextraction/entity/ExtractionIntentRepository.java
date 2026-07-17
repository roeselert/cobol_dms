package de.dms.aiextraction.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractionIntentRepository extends JpaRepository<ExtractionIntent, String> {

    List<ExtractionIntent> findAllByOrderByNameAsc();

    boolean existsByName(String name);
}
