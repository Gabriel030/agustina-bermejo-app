package com.red_escuela.backend.shared.audit;

import java.time.LocalDateTime;

public record AuditLogView(Long id, LocalDateTime occurredAt, Long actorPersonId, String actorRole,
                           AuditAction action, AuditEntityType entityType, Long entityId,
                           String beforeValue, String afterValue, String reason) {

    public static AuditLogView from(AuditLogEntry entry) {
        return new AuditLogView(entry.getId(), entry.getOccurredAt(), entry.getActorPersonId(), entry.getActorRole(),
                entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                entry.getBeforeValue(), entry.getAfterValue(), entry.getReason());
    }
}
