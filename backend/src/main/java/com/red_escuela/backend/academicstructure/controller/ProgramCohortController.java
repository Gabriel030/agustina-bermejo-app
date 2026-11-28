package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.dto.CohortReadinessResponse;
import com.red_escuela.backend.academicstructure.dto.CohortResponsiblePreceptorResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramTermCohortRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateCohortResponsiblesRequest;
import com.red_escuela.backend.academicstructure.usecase.cohort.ReplaceCohortResponsiblesUseCase;
import com.red_escuela.backend.academicstructure.dto.ProgramTermCohortResponse;
import com.red_escuela.backend.academicstructure.usecase.cohort.CreateProgramTermCohortUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.GetProgramCohortReadinessUseCase;
import com.red_escuela.backend.academicstructure.usecase.cohort.OpenProgramCohortUseCase;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProgramCohortController {

    private final CreateProgramTermCohortUseCase createProgramTermCohortUseCase;
    private final OpenProgramCohortUseCase openProgramCohortUseCase;
    private final GetProgramCohortReadinessUseCase getProgramCohortReadinessUseCase;
    private final ReplaceCohortResponsiblesUseCase replaceCohortResponsiblesUseCase;

    public ProgramCohortController(CreateProgramTermCohortUseCase createProgramTermCohortUseCase, OpenProgramCohortUseCase openProgramCohortUseCase,
                                   GetProgramCohortReadinessUseCase getProgramCohortReadinessUseCase,
                                   ReplaceCohortResponsiblesUseCase replaceCohortResponsiblesUseCase) {
        this.replaceCohortResponsiblesUseCase = replaceCohortResponsiblesUseCase;
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

    @PutMapping("/cohorts/{id}/responsible-preceptors")
    @RequiresPermission(code = "academicstructure.manage_preceptor_assignments", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<CohortResponsiblePreceptorResponse> replaceResponsiblePreceptors(@PathVariable Long id,
                                                                                @Valid @RequestBody UpdateCohortResponsiblesRequest request) {
        return replaceCohortResponsiblesUseCase.replace(id, request.responsiblePreceptorIds());
    }
}
