package de.dms.crosscutting.accesscontrol.entity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, String> {
}
