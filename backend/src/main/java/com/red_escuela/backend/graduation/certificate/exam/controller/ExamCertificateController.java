package com.red_escuela.backend.graduation.certificate.exam.controller;

import com.red_escuela.backend.graduation.certificate.exam.dto.ExamCertificateResponse;
import com.red_escuela.backend.graduation.certificate.exam.dto.RevokeExamCertificateRequest;
import com.red_escuela.backend.graduation.certificate.exam.usecase.RevokeExamCertificateUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExamCertificateController {

    private final RevokeExamCertificateUseCase revokeUseCase;

    public ExamCertificateController(RevokeExamCertificateUseCase revokeUseCase) {
        this.revokeUseCase = revokeUseCase;
    }

    @PostMapping("/exam-certificates/{id}/revoke")
    @RequiresPermission(code = "certificate.revoke", legacyRoles = "ROLE_DIRECTOR")
    public ExamCertificateResponse revoke(@PathVariable Long id, @Valid @RequestBody RevokeExamCertificateRequest request) {
        return revokeUseCase.revoke(id, request.reason());
    }
}
