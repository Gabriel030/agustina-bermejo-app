package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.academicstructure.usecase.GetProgramListPricesUseCase;
import com.red_escuela.backend.billing.dto.ProgramPricingResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.dto.SetCatalogVisibilityRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.service.ProgramService;
import com.red_escuela.backend.academicstructure.usecase.SetProgramCatalogVisibilityUseCase;
import com.red_escuela.backend.academicstructure.entity.Program;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/programs")
public class ProgramController {

    private final ProgramService programService;
    private final SetProgramCatalogVisibilityUseCase setCatalogVisibility;
    private final GetProgramListPricesUseCase programListPrices;

    public ProgramController(ProgramService programService,
            SetProgramCatalogVisibilityUseCase setCatalogVisibility,
            GetProgramListPricesUseCase programListPrices) {
        this.programService = programService;
        this.setCatalogVisibility = setCatalogVisibility;
        this.programListPrices = programListPrices;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse create(@Valid @RequestBody CreateProgramRequest request) {
        return withPrices(programService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProgramRequest request) {
        return withPrices(programService.update(id, request));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void deactivate(@PathVariable Long id) {
        programService.deactivate(id);
    }

    @PostMapping("/{id}/activate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public void reactivate(@PathVariable Long id) {
        programService.reactivate(id);
    }

    @GetMapping
    public List<ProgramResponse> list(@RequestParam(required = false) StatusType status,
            @RequestParam(required = false) Boolean visibleInCatalog) {
        return programService.list(status, visibleInCatalog).stream().map(this::withPrices).toList();
    }

    @PutMapping("/{id}/catalog-visibility")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse setCatalogVisibility(@PathVariable Long id,
            @Valid @RequestBody SetCatalogVisibilityRequest request) {
        return setCatalogVisibility.execute(id, request.visible());
    }

    @GetMapping("/{id}")
    public ProgramResponse get(@PathVariable Long id) {
        return withPrices(programService.get(id));
    }

    @GetMapping("/{id}/pricing")
    public ProgramPricingResponse getPricing(@PathVariable Long id) {
        programService.get(id);
        var prices = programListPrices.execute(id);
        return new ProgramPricingResponse(id, prices.enrollmentFee(), prices.enrollmentFee(), prices.tuition());
    }

    private ProgramResponse withPrices(Program program) {
        var prices = programListPrices.execute(program.getId());
        return ProgramResponse.from(program, prices.tuition(), prices.enrollmentFee());
    }
}