package com.red_escuela.backend.shared.audit;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AuditLogView(Long id, LocalDateTime occurredAt, Long actorPersonId, String actorRole, String actorName,
                           @Schema(type = "string", example = "STAFF_WITHDRAWN",
                                   description = "Acción registrada. Conjunto abierto: pueden aparecer valores nuevos, por ejemplo STAFF_WITHDRAWN, PASSWORD_CHANGED o DATA_IMPORT_COMMITTED")
                           AuditAction action,
                           @Schema(type = "string", example = "PERSON",
                                   description = "Tipo de entidad afectada. Conjunto abierto: pueden aparecer valores nuevos, por ejemplo PERSON o STUDENT")
                           AuditEntityType entityType,
                           Long entityId, String beforeValue, String afterValue, String reason) {

    public static final String SYSTEM_ACTOR_NAME = "Sistema";

    public static AuditLogView from(AuditLogEntry entry, String actorName) {
        String resolvedName = entry.getActorPersonId() == null ? SYSTEM_ACTOR_NAME : actorName;
        return new AuditLogView(entry.getId(), entry.getOccurredAt(), entry.getActorPersonId(), entry.getActorRole(),
                resolvedName, entry.getAction(), entry.getEntityType(), entry.getEntityId(),
                AuditValueMasker.mask(entry.getBeforeValue()), AuditValueMasker.mask(entry.getAfterValue()),
                entry.getReason());
    }
}
