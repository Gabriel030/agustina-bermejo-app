package com.red_escuela.backend.graduation.certificate.approvedsubjects.controller;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RevokeApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.RevokeApprovedSubjectsCertificateUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
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
    @RequiresPermission(code = "certificate.revoke", legacyRoles = "ROLE_DIRECTOR")
    public ApprovedSubjectsCertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeApprovedSubjectsCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }
}
