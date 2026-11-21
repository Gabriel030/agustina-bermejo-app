package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.usecase.audit.ListAuditLogUseCase;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final ListAuditLogUseCase listAuditLogUseCase;

    public AuditLogController(ListAuditLogUseCase listAuditLogUseCase) {
        this.listAuditLogUseCase = listAuditLogUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "audit.read", legacyRoles = "ROLE_DIRECTOR")
    public PageResponse<AuditLogView> listAuditLog(@RequestParam(required = false) List<AuditAction> action,
                                                   @RequestParam(required = false) AuditEntityType entityType,
                                                   @RequestParam(required = false) Long entityId,
                                                   @RequestParam(required = false) Long actorPersonId,
                                                   @RequestParam(required = false) String actorName,
                                                   @RequestParam(required = false) List<String> actorRole,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return listAuditLogUseCase.list(
                new AuditLogFilter(action, entityType, entityId, actorPersonId, actorRole, from, to), actorName, page, size);
    }
}
