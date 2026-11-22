package com.red_escuela.backend.academicstructure.controller;

import com.red_escuela.backend.billing.dto.ProgramPricingResponse;
import com.red_escuela.backend.academicstructure.dto.CreateProgramRequest;
import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.dto.SetCatalogVisibilityRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateProgramRequest;
import com.red_escuela.backend.academicstructure.service.ProgramService;
import com.red_escuela.backend.academicstructure.usecase.GetCatalogPriceVisibilityUseCase;
import com.red_escuela.backend.academicstructure.usecase.SetProgramCatalogVisibilityUseCase;
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
    private final GetCatalogPriceVisibilityUseCase catalogPriceVisibility;

    public ProgramController(ProgramService programService,
            SetProgramCatalogVisibilityUseCase setCatalogVisibility,
            GetCatalogPriceVisibilityUseCase catalogPriceVisibility) {
        this.programService = programService;
        this.setCatalogVisibility = setCatalogVisibility;
        this.catalogPriceVisibility = catalogPriceVisibility;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse create(@Valid @RequestBody CreateProgramRequest request) {
        return ProgramResponse.from(programService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProgramRequest request) {
        return ProgramResponse.from(programService.update(id, request));
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
        boolean pricesVisible = catalogPriceVisibility.execute();
        return programService.list(status, visibleInCatalog).stream()
                .map(ProgramResponse::from)
                .map(response -> pricesVisible ? response : response.withoutPrices())
                .toList();
    }

    @PutMapping("/{id}/catalog-visibility")
    @PreAuthorize("hasRole('ADMINISTRATIVO')")
    public ProgramResponse setCatalogVisibility(@PathVariable Long id,
            @Valid @RequestBody SetCatalogVisibilityRequest request) {
        return setCatalogVisibility.execute(id, request.visible());
    }

    @GetMapping("/{id}")
    public ProgramResponse get(@PathVariable Long id) {
        var response = ProgramResponse.from(programService.get(id));
        return catalogPriceVisibility.execute() ? response : response.withoutPrices();
    }

    @GetMapping("/{id}/pricing")
    public ProgramPricingResponse getPricing(@PathVariable Long id) {
        var program = programService.get(id);
        if (!catalogPriceVisibility.execute()) {
            return new ProgramPricingResponse(id, null, null, null);
        }
        return new ProgramPricingResponse(id, program.enrollmentFeeAsMoney(), program.enrollmentFeeAsMoney(), program.priceAsMoney());
    }
}