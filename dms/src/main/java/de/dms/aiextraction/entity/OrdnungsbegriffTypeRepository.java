package de.dms.aiextraction.entity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrdnungsbegriffTypeRepository extends JpaRepository<OrdnungsbegriffType, String> {

    List<OrdnungsbegriffType> findAllByOrderByNameAsc();

    List<OrdnungsbegriffType> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);
}
