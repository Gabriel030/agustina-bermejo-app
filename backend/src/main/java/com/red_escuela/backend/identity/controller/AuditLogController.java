package com.red_escuela.backend.identity.controller;

import com.red_escuela.backend.identity.usecase.audit.ListAuditLogUseCase;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditLogView;
import com.red_escuela.backend.shared.pagination.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/audit-log")
public class AuditLogController {

    private final ListAuditLogUseCase listAuditLogUseCase;

    public AuditLogController(ListAuditLogUseCase listAuditLogUseCase) {
        this.listAuditLogUseCase = listAuditLogUseCase;
    }

    @GetMapping
    @PreAuthorize("hasRole('DIRECTOR')")
    public PageResponse<AuditLogView> listAuditLog(@RequestParam(required = false) AuditAction action,
                                                   @RequestParam(required = false) AuditEntityType entityType,
                                                   @RequestParam(required = false) Long entityId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "50") int size) {
        return listAuditLogUseCase.list(action, entityType, entityId, page, size);
    }
}
