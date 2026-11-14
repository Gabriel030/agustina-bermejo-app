package com.red_escuela.backend.shared.audit;

import java.util.Objects;

public record AuditRecord(AuditAction action, AuditEntityType entityType, Long entityId,
                          String beforeValue, String afterValue, String reason) {

    public AuditRecord {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(entityId, "entityId");
    }
}
