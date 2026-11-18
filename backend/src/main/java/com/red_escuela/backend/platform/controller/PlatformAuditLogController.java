package com.red_escuela.backend.platform.controller;

import com.red_escuela.backend.platform.usecase.ListPlatformAuditLogUseCase;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogFilter;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
public class PlatformAuditLogController {

    private final ListPlatformAuditLogUseCase listPlatformAuditLogUseCase;

    public PlatformAuditLogController(ListPlatformAuditLogUseCase listPlatformAuditLogUseCase) {
        this.listPlatformAuditLogUseCase = listPlatformAuditLogUseCase;
    }

    @GetMapping("/platform/audit-log")
    public PageResponse<AuditLogView> listAuditLog(@RequestParam(required = false) List<AuditAction> action,
                                                   @RequestParam(required = false) AuditEntityType entityType,
                                                   @RequestParam(required = false) Long entityId,
                                                   @RequestParam(required = false) Long actorPersonId,
                                                   @RequestParam(required = false) List<String> actorRole,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return listPlatformAuditLogUseCase.list(
                new AuditLogFilter(action, entityType, entityId, actorPersonId, actorRole, from, to), page, size);
    }
}
