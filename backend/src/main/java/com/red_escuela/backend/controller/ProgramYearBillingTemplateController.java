package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.dto.CreateProgramYearBillingTemplateRequest;
import com.red_escuela.backend.dto.ProgramCohortResponse;
import com.red_escuela.backend.dto.ProgramYearBillingTemplateResponse;
import com.red_escuela.backend.service.ProgramCohortService;
import com.red_escuela.backend.service.ProgramYearBillingTemplateService;
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

    private final ProgramYearBillingTemplateService templateService;
    private final ProgramCohortService programCohortService;

    public ProgramYearBillingTemplateController(ProgramYearBillingTemplateService templateService, ProgramCohortService programCohortService) {
        this.templateService = templateService;
        this.programCohortService = programCohortService;
    }

    @PostMapping("/programs/{id}/year-billing-templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramYearBillingTemplateResponse crear(@PathVariable Long id, @Valid @RequestBody CreateProgramYearBillingTemplateRequest request) {
        return ProgramYearBillingTemplateResponse.from(templateService.crear(id, request.termId(), request.recurringFeeInvoicesCount()));
    }

    @PostMapping("/year-billing-templates/{id}/cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramCohortResponse crearCohorte(@PathVariable Long id, @Valid @RequestBody CreateProgramCohortRequest request) {
        return ProgramCohortResponse.from(programCohortService.crear(id, request));
    }

    // Also reachable by student self-service to choose a cohort at enrollment time.
    @GetMapping("/year-billing-templates/{id}/cohorts")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR', 'ALUMNO')")
    public List<ProgramCohortResponse> listarCohortes(@PathVariable Long id) {
        return programCohortService.listarPorPlantilla(id).stream().map(ProgramCohortResponse::from).toList();
    }
}
