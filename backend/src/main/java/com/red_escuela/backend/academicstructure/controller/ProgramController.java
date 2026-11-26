package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.billing.dto.ProgramPricingResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.dto.SetCatalogVisibilityRequest;
import com.red_escuela.backend.academicstructure.entity.CatalogArea;
import com.red_escuela.backend.academicstructure.usecase.SetCatalogVisibilityCommand;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.service.ProgramService;
import com.red_escuela.backend.academicstructure.usecase.SetProgramCatalogVisibilityUseCase;
import com.red_escuela.backend.shared.module.HidesPricesWhenBillingOff;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import com.red_escuela.backend.shared.permission.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.red_escuela.backend.shared.domain.StatusType;

import java.util.List;

@RestController
@HidesPricesWhenBillingOff
@RequestMapping("/programs")
public class ProgramController {

    private final ProgramService programService;
    private final SetProgramCatalogVisibilityUseCase setCatalogVisibility;

    public ProgramController(ProgramService programService,
            SetProgramCatalogVisibilityUseCase setCatalogVisibility) {
        this.programService = programService;
        this.setCatalogVisibility = setCatalogVisibility;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ProgramResponse create(@Valid @RequestBody CreateProgramRequest request) {
        return ProgramResponse.from(programService.create(request));
    }

    @PutMapping("/{id}")
    @RequiresPermission(code = "academicstructure.manage_programs", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ProgramResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProgramRequest request) {
        return ProgramResponse.from(programService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_program_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void deactivate(@PathVariable Long id) {
        programService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(code = "academicstructure.change_program_status", legacyRoles = "ROLE_ADMINISTRATIVO")
    public void reactivate(@PathVariable Long id) {
        programService.reactivate(id);
    }

    @GetMapping
    public List<ProgramResponse> list(@RequestParam(required = false) StatusType status,
            @RequestParam(required = false) Boolean visibleInCatalog,
            @RequestParam(required = false) CatalogArea area) {
        return programService.list(status, visibleInCatalog, area).stream().map(ProgramResponse::from).toList();
    }

    @PutMapping("/{id}/catalog-visibility")
    @RequiresPermission(code = "academicstructure.change_catalog_visibility", legacyRoles = "ROLE_ADMINISTRATIVO")
    public ProgramResponse setCatalogVisibility(@PathVariable Long id,
            @Valid @RequestBody SetCatalogVisibilityRequest request) {
        return setCatalogVisibility.execute(new SetCatalogVisibilityCommand(id, request.visible(), request.area()));
    }

    @GetMapping("/{id}")
    public ProgramResponse get(@PathVariable Long id) {
        return ProgramResponse.from(programService.get(id));
    }

    @GetMapping("/{id}/pricing")
    public ProgramPricingResponse getPricing(@PathVariable Long id) {
        var program = programService.get(id);
        return new ProgramPricingResponse(id, program.enrollmentFeeAsMoney(), program.enrollmentFeeAsMoney(), program.priceAsMoney());
    }
}