package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgramCohortController {

    private final CreateProgramTermCohortUseCase createProgramTermCohortUseCase;

    public ProgramCohortController(CreateProgramTermCohortUseCase createProgramTermCohortUseCase) {
        this.createProgramTermCohortUseCase = createProgramTermCohortUseCase;
    }

    @PostMapping("/programs/{programId}/terms/{termId}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_program_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramTermCohortResponse createProgramTermCohort(@PathVariable Long programId, @PathVariable Long termId,
                                                             @Valid @RequestBody CreateProgramTermCohortRequest request) {
        return createProgramTermCohortUseCase.createProgramTermCohort(programId, termId, request);
    }
}
