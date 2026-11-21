package com.red_escuela.backend.shared.audit;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public record AuditLogFilter(List<AuditAction> actions, AuditEntityType entityType, Long entityId, Long actorPersonId,
                             List<String> actorRoles, LocalDate from, LocalDate to, List<Long> actorPersonIds) {

    public AuditLogFilter(List<AuditAction> actions, AuditEntityType entityType, Long entityId, Long actorPersonId,
                          List<String> actorRoles, LocalDate from, LocalDate to) {
        this(actions, entityType, entityId, actorPersonId, actorRoles, from, to, null);
    }

    public AuditLogFilter {
        actions = actions == null ? List.of() : actions.stream().filter(Objects::nonNull).distinct().toList();
        actorRoles = actorRoles == null ? List.of() : actorRoles.stream().filter(role -> role != null && !role.isBlank())
                .map(String::trim).distinct().toList();
    }

    public AuditLogFilter restrictedToActors(List<Long> personIds) {
        return new AuditLogFilter(actions, entityType, entityId, actorPersonId, actorRoles, from, to, personIds);
    }

    public static AuditLogFilter none() {
        return new AuditLogFilter(null, null, null, null, null, null, null);
    }
}
