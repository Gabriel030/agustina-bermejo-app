package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.dto.CreateProgramYearBillingTemplateRequest;
import com.red_escuela.backend.billing.dto.ProgramCohortResponse;
import com.red_escuela.backend.billing.dto.ProgramYearBillingTemplateResponse;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramCohortUseCase;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramCohortsForTemplateUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ProgramYearBillingTemplateController {

    private final CreateProgramYearBillingTemplateUseCase createTemplateUseCase;
    private final CreateProgramCohortUseCase createCohortUseCase;
    private final ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase;

    public ProgramYearBillingTemplateController(CreateProgramYearBillingTemplateUseCase createTemplateUseCase,
                                                  CreateProgramCohortUseCase createCohortUseCase,
                                                  ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase) {
        this.createTemplateUseCase = createTemplateUseCase;
        this.createCohortUseCase = createCohortUseCase;
        this.listCohortsForTemplateUseCase = listCohortsForTemplateUseCase;
    }

    @PostMapping("/programs/{id}/year-billing-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramYearBillingTemplateResponse crear(@PathVariable Long id, @Valid @RequestBody CreateProgramYearBillingTemplateRequest request) {
        return ProgramYearBillingTemplateResponse.from(createTemplateUseCase.create(id, request.termId(), request.recurringFeeInvoicesCount()));
    }

    @PostMapping("/year-billing-templates/{id}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramCohortResponse crearCohorte(@PathVariable Long id, @Valid @RequestBody CreateProgramCohortRequest request) {
        return ProgramCohortResponse.from(createCohortUseCase.create(id, request));
    }

    // Also reachable by student self-service to choose a cohort at enrollment time.
    @GetMapping("/year-billing-templates/{id}/cohorts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'ALUMNO')")
    public List<ProgramCohortResponse> listarCohortes(@PathVariable Long id) {
        return listCohortsForTemplateUseCase.list(id).stream().map(ProgramCohortResponse::from).toList();
    }
}
