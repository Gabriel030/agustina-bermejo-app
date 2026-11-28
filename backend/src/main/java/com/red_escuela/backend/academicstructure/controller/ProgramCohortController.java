package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.GetProgramCohortReadinessUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.OpenProgramCohortUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgramCohortController {

    private final CreateProgramTermCohortUseCase createProgramTermCohortUseCase;
    private final OpenProgramCohortUseCase openProgramCohortUseCase;
    private final GetProgramCohortReadinessUseCase getProgramCohortReadinessUseCase;

    public ProgramCohortController(CreateProgramTermCohortUseCase createProgramTermCohortUseCase, OpenProgramCohortUseCase openProgramCohortUseCase,
                                   GetProgramCohortReadinessUseCase getProgramCohortReadinessUseCase) {
        this.createProgramTermCohortUseCase = createProgramTermCohortUseCase;
        this.openProgramCohortUseCase = openProgramCohortUseCase;
        this.getProgramCohortReadinessUseCase = getProgramCohortReadinessUseCase;
    }

    @PostMapping("/programs/{programId}/terms/{termId}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_program_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramTermCohortResponse createProgramTermCohort(@PathVariable Long programId, @PathVariable Long termId,
                                                             @Valid @RequestBody CreateProgramTermCohortRequest request) {
        return createProgramTermCohortUseCase.createProgramTermCohort(programId, termId, request);
    }

    @GetMapping("/cohorts/{id}/readiness")
    @RequiresPermission(code = "academicstructure.manage_program_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public CohortReadinessResponse getProgramCohortReadiness(@PathVariable Long id) {
        return getProgramCohortReadinessUseCase.getProgramCohortReadiness(id);
    }

    @PostMapping("/cohorts/{id}/open")
    @RequiresPermission(code = "academicstructure.manage_program_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramTermCohortResponse openProgramCohort(@PathVariable Long id) {
        return openProgramCohortUseCase.openProgramCohort(id);
    }
}
