package com.red_escuela.backend.graduation.certificate.approvedsubjects.controller;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RevokeApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.RevokeApprovedSubjectsCertificateUseCase;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class ApprovedSubjectsCertificateController {

    private final RevokeApprovedSubjectsCertificateUseCase revokeUseCase;

    public ApprovedSubjectsCertificateController(RevokeApprovedSubjectsCertificateUseCase revokeUseCase) {
        this.revokeUseCase = revokeUseCase;
    }

    @PostMapping("/approved-subjects-certificates/{id}/revoke")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ApprovedSubjectsCertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeApprovedSubjectsCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }
}
