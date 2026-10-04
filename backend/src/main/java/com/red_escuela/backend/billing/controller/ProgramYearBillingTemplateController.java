package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.dto.CreateProgramYearBillingTemplateRequest;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.billing.dto.ProgramCohortResponse;
import com.red_escuela.backend.billing.dto.ProgramYearBillingTemplateResponse;
import com.red_escuela.backend.billing.dto.UpdateEntryFeeDiscountRequest;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateYearBillingConfigurationUseCase;
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

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@RestController
public class ProgramYearBillingTemplateController {
    private final CreateProgramYearBillingTemplateUseCase createTemplateUseCase;
    private final CreateProgramCohortUseCase createCohortUseCase;
    private final ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase;
    private final ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase;
    private final ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase;
    private final UpdateYearBillingConfigurationUseCase updateYearConfigurationUseCase;
    private final UpdateCohortEntryFeeDiscountUseCase updateEntryFeeDiscountUseCase;
    private final Clock clock;

    public ProgramYearBillingTemplateController(CreateProgramYearBillingTemplateUseCase createTemplateUseCase,
                                                  CreateProgramCohortUseCase createCohortUseCase,
                                                  ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase,
                                                  ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase,
                                                  ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase,
                                                  UpdateYearBillingConfigurationUseCase updateYearConfigurationUseCase,
                                                  UpdateCohortEntryFeeDiscountUseCase updateEntryFeeDiscountUseCase,
                                                  Clock clock) {
        this.updateYearConfigurationUseCase = updateYearConfigurationUseCase;
        this.updateEntryFeeDiscountUseCase = updateEntryFeeDiscountUseCase;
        this.createTemplateUseCase = createTemplateUseCase;
        this.createCohortUseCase = createCohortUseCase;
        this.listCohortsForTemplateUseCase = listCohortsForTemplateUseCase;
        this.listCohortsForProgramUseCase = listCohortsForProgramUseCase;
        this.listBillingTemplatesUseCase = listBillingTemplatesUseCase;
        this.clock = clock;
    }

    @PostMapping("/programs/{id}/year-billing-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramYearBillingTemplateResponse create(@PathVariable Long id, @Valid @RequestBody CreateProgramYearBillingTemplateRequest request) {
        return ProgramYearBillingTemplateResponse.from(createTemplateUseCase.create(id, request.termId(), request.recurringFeeInvoicesCount(), request.configuration()));
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

    @PatchMapping("/year-billing-templates/{id}/configuration")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramYearBillingTemplateResponse updateConfiguration(@PathVariable Long id, @Valid @RequestBody YearBillingConfigurationRequest request) {
        return ProgramYearBillingTemplateResponse.from(updateYearConfigurationUseCase.update(id, request));
    }

    @PatchMapping("/cohorts/{id}/entry-fee-discount")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public ProgramCohortResponse updateEntryFeeDiscount(@PathVariable Long id, @Valid @RequestBody UpdateEntryFeeDiscountRequest request) {
        return ProgramCohortResponse.from(updateEntryFeeDiscountUseCase.update(id, request.discount()));
    }

    @GetMapping("/programs/{id}/cohorts")
    @PreAuthorize("isAuthenticated()")
    public List<ProgramCohortCatalogResponse> listProgramCohorts(@PathVariable Long id) {
        return listCohortsForProgramUseCase.list(id).stream().map(c -> ProgramCohortCatalogResponse.from(c, id, LocalDate.now(clock))).toList();
    }
}
