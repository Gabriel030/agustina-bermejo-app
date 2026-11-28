package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.IncompleteAcademicOfferResponse;
import com.red_escuela.backend.academicstructure.usecase.readiness.GetIncompleteAcademicOfferUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AcademicReadinessController {

    private final GetIncompleteAcademicOfferUseCase getIncompleteAcademicOfferUseCase;

    public AcademicReadinessController(GetIncompleteAcademicOfferUseCase getIncompleteAcademicOfferUseCase) {
        this.getIncompleteAcademicOfferUseCase = getIncompleteAcademicOfferUseCase;
    }

    @GetMapping("/academic-readiness/incomplete")
    @RequiresPermission(code = "academicstructure.manage_program_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public IncompleteAcademicOfferResponse getIncompleteAcademicOffer() {
        return getIncompleteAcademicOfferUseCase.getIncompleteAcademicOffer();
    }
}
