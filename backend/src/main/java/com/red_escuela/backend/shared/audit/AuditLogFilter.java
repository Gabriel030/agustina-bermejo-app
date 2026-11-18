package com.red_escuela.backend.shared.audit;

import java.time.LocalDate;
import java.util.List;

public record AuditLogFilter(List<AuditAction> actions, AuditEntityType entityType, Long entityId, Long actorPersonId,
                             List<String> actorRoles, LocalDate from, LocalDate to) {

    public AuditLogFilter {
        actions = actions == null ? List.of() : actions.stream().filter(java.util.Objects::nonNull).distinct().toList();
        actorRoles = actorRoles == null ? List.of() : actorRoles.stream().filter(role -> role != null && !role.isBlank())
                .map(String::trim).distinct().toList();
    }

    public static AuditLogFilter none() {
        return new AuditLogFilter(null, null, null, null, null, null, null);
    }
}
