package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.dto.CreateProgramYearBillingTemplateRequest;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.billing.dto.ProgramCohortResponse;
import com.red_escuela.backend.billing.dto.ProgramYearBillingTemplateResponse;
import com.red_escuela.backend.billing.dto.UpdateEnrollmentFeeMonthRequest;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEnrollmentFeeMonthUseCase;
import org.springframework.web.bind.annotation.PatchMapping;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramCohortUseCase;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramBillingTemplatesUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramCohortsForProgramUseCase;
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
    private final ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase;
    private final ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase;
    private final UpdateCohortEnrollmentFeeMonthUseCase updateEnrollmentFeeMonthUseCase;

    public ProgramYearBillingTemplateController(CreateProgramYearBillingTemplateUseCase createTemplateUseCase,
                                                  CreateProgramCohortUseCase createCohortUseCase,
                                                  ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase,
                                                  ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase,
                                                  ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase,
                                                  UpdateCohortEnrollmentFeeMonthUseCase updateEnrollmentFeeMonthUseCase) {
        this.createTemplateUseCase = createTemplateUseCase;
        this.createCohortUseCase = createCohortUseCase;
        this.listCohortsForTemplateUseCase = listCohortsForTemplateUseCase;
        this.listCohortsForProgramUseCase = listCohortsForProgramUseCase;
        this.listBillingTemplatesUseCase = listBillingTemplatesUseCase;
        this.updateEnrollmentFeeMonthUseCase = updateEnrollmentFeeMonthUseCase;
    }

    @PostMapping("/programs/{id}/year-billing-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramYearBillingTemplateResponse create(@PathVariable Long id, @Valid @RequestBody CreateProgramYearBillingTemplateRequest request) {
        return ProgramYearBillingTemplateResponse.from(createTemplateUseCase.create(id, request.termId(), request.recurringFeeInvoicesCount()));
    }

    @PostMapping("/year-billing-templates/{id}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ProgramCohortResponse createCohort(@PathVariable Long id, @Valid @RequestBody CreateProgramCohortRequest request) {
        return ProgramCohortResponse.from(createCohortUseCase.create(id, request));
    }

    @GetMapping("/year-billing-templates/{id}/cohorts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'ALUMNO')")
    public List<ProgramCohortResponse> listCohorts(@PathVariable Long id) {
        return listCohortsForTemplateUseCase.list(id).stream().map(ProgramCohortResponse::from).toList();
    }

    @GetMapping("/programs/{id}/year-billing-templates")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<ProgramYearBillingTemplateResponse> listBillingTemplates(@PathVariable Long id) {
        return listBillingTemplatesUseCase.list(id).stream().map(ProgramYearBillingTemplateResponse::from).toList();
    }

    @PatchMapping("/cohorts/{id}/enrollment-fee-month")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ProgramCohortResponse updateEnrollmentFeeMonth(@PathVariable Long id, @Valid @RequestBody UpdateEnrollmentFeeMonthRequest request) {
        return ProgramCohortResponse.from(updateEnrollmentFeeMonthUseCase.update(id, request.enrollmentFeeMonth()));
    }

    @GetMapping("/programs/{id}/cohorts")
    @PreAuthorize("isAuthenticated()")
    public List<ProgramCohortCatalogResponse> listProgramCohorts(@PathVariable Long id) {
        return listCohortsForProgramUseCase.list(id).stream().map(c -> ProgramCohortCatalogResponse.from(c, id)).toList();
    }
}
