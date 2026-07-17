package de.dms.crosscutting.accesscontrol.control;

import de.dms.crosscutting.accesscontrol.entity.AuditAction;
import de.dms.crosscutting.accesscontrol.entity.AuditEffect;
import de.dms.crosscutting.accesscontrol.entity.AuditLogEntry;
import de.dms.crosscutting.accesscontrol.entity.AuditLogEntryRepository;
import de.dms.crosscutting.security.control.UserRef;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes an audit entry for every allow/deny decision before the result is
 * returned to the caller (S-3). Authorization checks run before the boundary
 * opens its transaction, so a later rollback never erases a decision record.
 */
@Service
public class AuditTrail {

    private final AuditLogEntryRepository entries;

    public AuditTrail(AuditLogEntryRepository entries) {
        this.entries = entries;
    }

    public void record(UserRef user, AuditAction action, ResourceRef resource, AuditEffect effect) {
        entries.save(new AuditLogEntry(
                UUID.randomUUID().toString(),
                user == null ? null : user.id(),
                action,
                resource.type(),
                resource.id(),
                effect,
                Instant.now().toEpochMilli(),
                sourceIp()));
    }

    private String sourceIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
