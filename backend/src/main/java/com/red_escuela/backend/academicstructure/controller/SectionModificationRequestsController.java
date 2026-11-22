package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;
import com.red_escuela.backend.academicstructure.usecase.cycle.ListPendingSectionModificationsUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class SectionModificationRequestsController {

    private final ListPendingSectionModificationsUseCase listPending;

    public SectionModificationRequestsController(ListPendingSectionModificationsUseCase listPending) {
        this.listPending = listPending;
    }

    @GetMapping("/section-modification-requests/pending")
    @RequiresPermission(code = "academicstructure.read_pending_section_modifications", legacyRoles = "ROLE_DIRECTOR")
    public List<SectionModificationRequestResponse> listPendingRequests() {
        return listPending.list();
    }
}
