package de.dms.aiextraction.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExtractionIntentFieldRepository extends JpaRepository<ExtractionIntentField, String> {

    List<ExtractionIntentField> findByIntentIdOrderByNameAsc(String intentId);

    void deleteByIntentId(String intentId);
}
