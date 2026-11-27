package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.billing.dto.CreateProgramYearBillingTemplateRequest;
import com.red_escuela.backend.billing.dto.ProgramCohortCatalogResponse;
import com.red_escuela.backend.billing.dto.ProgramCohortResponse;
import com.red_escuela.backend.billing.advanceenrollmentfee.usecase.FindAdvanceEnrollmentFeeAlertsUseCase;
import com.red_escuela.backend.billing.dto.ProgramYearBillingTemplateResponse;
import com.red_escuela.backend.billing.dto.UpdateEntryFeeDiscountRequest;
import com.red_escuela.backend.billing.dto.YearBillingConfigurationRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.billing.entity.ProgramYearBillingTemplate;
import com.red_escuela.backend.billing.configuration.usecase.FindProgramBillingConfigurationUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateCohortEntryFeeDiscountUseCase;
import com.red_escuela.backend.billing.usecase.cohort.UpdateYearBillingConfigurationUseCase;
import org.springframework.web.bind.annotation.PatchMapping;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramCohortUseCase;
import com.red_escuela.backend.billing.usecase.cohort.CreateProgramYearBillingTemplateUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramBillingTemplatesUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramCohortsForProgramUseCase;
import com.red_escuela.backend.billing.usecase.cohort.ListProgramCohortsForTemplateUseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
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
@RequiresModule(RequiresModule.BILLING)
public class ProgramYearBillingTemplateController {
    private final CreateProgramYearBillingTemplateUseCase createTemplateUseCase;
    private final CreateProgramCohortUseCase createCohortUseCase;
    private final ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase;
    private final ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase;
    private final ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase;
    private final FindProgramBillingConfigurationUseCase findConfigurationUseCase;
    private final UpdateYearBillingConfigurationUseCase updateYearConfigurationUseCase;
    private final UpdateCohortEntryFeeDiscountUseCase updateEntryFeeDiscountUseCase;
    private final FindAdvanceEnrollmentFeeAlertsUseCase findAdvanceAlertsUseCase;
    private final Clock clock;

    public ProgramYearBillingTemplateController(CreateProgramYearBillingTemplateUseCase createTemplateUseCase,
                                                  CreateProgramCohortUseCase createCohortUseCase,
                                                  ListProgramCohortsForTemplateUseCase listCohortsForTemplateUseCase, FindProgramBillingConfigurationUseCase findConfigurationUseCase,
                                                  ListProgramCohortsForProgramUseCase listCohortsForProgramUseCase,
                                                  ListProgramBillingTemplatesUseCase listBillingTemplatesUseCase,
                                                  UpdateYearBillingConfigurationUseCase updateYearConfigurationUseCase,
                                                  UpdateCohortEntryFeeDiscountUseCase updateEntryFeeDiscountUseCase,
                                                  FindAdvanceEnrollmentFeeAlertsUseCase findAdvanceAlertsUseCase, Clock clock) {
        this.updateYearConfigurationUseCase = updateYearConfigurationUseCase;
        this.updateEntryFeeDiscountUseCase = updateEntryFeeDiscountUseCase;
        this.createTemplateUseCase = createTemplateUseCase;
        this.createCohortUseCase = createCohortUseCase;
        this.listCohortsForTemplateUseCase = listCohortsForTemplateUseCase;
        this.listCohortsForProgramUseCase = listCohortsForProgramUseCase;
        this.listBillingTemplatesUseCase = listBillingTemplatesUseCase;
        this.findAdvanceAlertsUseCase = findAdvanceAlertsUseCase;
        this.clock = clock;
        this.findConfigurationUseCase = findConfigurationUseCase;
    }

    private ProgramYearBillingTemplateResponse templateResponse(ProgramYearBillingTemplate template) {
        return ProgramYearBillingTemplateResponse.from(template, findConfigurationUseCase.findYearTermsInEffect(template.programId(), template.termId()),
                findAdvanceAlertsUseCase.find(template.getId()));
    }

    private ProgramCohortResponse cohortResponse(ProgramCohort cohort) {
        return ProgramCohortResponse.from(cohort, findConfigurationUseCase.findEntryDiscountInEffect(cohort.getId()));
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/programs/{id}/year-billing-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "billing.manage_year_billing_template", legacyRoles = "ROLE_DIRECTOR")
    public ProgramYearBillingTemplateResponse create(@PathVariable Long id, @Valid @RequestBody CreateProgramYearBillingTemplateRequest request) {
        return templateResponse(createTemplateUseCase.create(id, request.termId(), request.recurringFeeInvoicesCount(), request.configuration()));
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping("/year-billing-templates/{id}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "billing.manage_year_billing_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramCohortResponse createCohort(@PathVariable Long id, @Valid @RequestBody CreateProgramCohortRequest request) {
        return cohortResponse(createCohortUseCase.create(id, request));
    }

    @GetMapping("/year-billing-templates/{id}/cohorts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'ALUMNO')")
    public List<ProgramCohortResponse> listCohorts(@PathVariable Long id) {
        return listCohortsForTemplateUseCase.list(id).stream().map(this::cohortResponse).toList();
    }

    @GetMapping("/programs/{id}/year-billing-templates")
    @RequiresPermission(code = "billing.read_year_billing_templates", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<ProgramYearBillingTemplateResponse> listBillingTemplates(@PathVariable Long id) {
        return listBillingTemplatesUseCase.list(id).stream().map(this::templateResponse).toList();
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PatchMapping("/year-billing-templates/{id}/configuration")
    @RequiresPermission(code = "billing.manage_year_billing_template", legacyRoles = "ROLE_DIRECTOR")
    public ProgramYearBillingTemplateResponse updateConfiguration(@PathVariable Long id, @Valid @RequestBody YearBillingConfigurationRequest request) {
        return templateResponse(updateYearConfigurationUseCase.update(id, request));
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PatchMapping("/cohorts/{id}/entry-fee-discount")
    @RequiresPermission(code = "billing.manage_year_billing_cohorts", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public ProgramCohortResponse updateEntryFeeDiscount(@PathVariable Long id, @Valid @RequestBody UpdateEntryFeeDiscountRequest request) {
        return cohortResponse(updateEntryFeeDiscountUseCase.update(id, request.discountPercent()));
    }

    @GetMapping("/programs/{id}/cohorts")
    @PreAuthorize("isAuthenticated()")
    public List<ProgramCohortCatalogResponse> listProgramCohorts(@PathVariable Long id) {
        return listCohortsForProgramUseCase.list(id).stream().map(c -> ProgramCohortCatalogResponse.from(c, id, LocalDate.now(clock))).toList();
    }
}
