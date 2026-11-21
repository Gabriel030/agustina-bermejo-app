package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.SectionModificationRequestResponse;
import com.red_escuela.backend.academicstructure.usecase.cycle.ListPendingSectionModificationsUseCase;
import org.springframework.security.access.prepost.PreAuthorize;
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
    @PreAuthorize("hasRole('DIRECTOR')")
    public List<SectionModificationRequestResponse> listPendingRequests() {
        return listPending.list();
    }
}
