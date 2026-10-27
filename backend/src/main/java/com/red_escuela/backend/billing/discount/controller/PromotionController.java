package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.usecase.ClosePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.CreatePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.FindLostCohortPromotionBenefitsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListActivePromotionsByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PromotionController {
    private final CreatePromotionByLegacyContractUseCase createUseCase;
    private final ClosePromotionByLegacyContractUseCase closeUseCase;
    private final ListActivePromotionsByLegacyContractUseCase listUseCase;
    private final FindLostCohortPromotionBenefitsUseCase lostBenefitsUseCase;

    public PromotionController(CreatePromotionByLegacyContractUseCase createUseCase, ClosePromotionByLegacyContractUseCase closeUseCase,
                                       ListActivePromotionsByLegacyContractUseCase listUseCase, FindLostCohortPromotionBenefitsUseCase lostBenefitsUseCase) {
        this.createUseCase = createUseCase;
        this.closeUseCase = closeUseCase;
        this.listUseCase = listUseCase;
        this.lostBenefitsUseCase = lostBenefitsUseCase;
    }

    @Operation(deprecated = true, description = "Deprecado (D-432): usar POST /discount-definitions con tipo COHORT_PROMOTION. Se retira cuando el front use el contrato nuevo.")
    @PostMapping("/courses/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return createUseCase.createForCourse(id, request);
    }

    @Operation(deprecated = true, description = "Deprecado (D-432): usar POST /discount-definitions con tipo COHORT_PROMOTION. Se retira cuando el front use el contrato nuevo.")
    @PostMapping("/programs/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForProgram(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return createUseCase.createForProgram(id, request);
    }

    @Operation(deprecated = true, description = "Deprecado (D-432): usar POST /discount-definitions/{id}/closure. Se retira cuando el front use el contrato nuevo.")
    @PostMapping("/promotions/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void close(@PathVariable Long id) {
        closeUseCase.closePromotion(id);
    }

    @Operation(deprecated = true, description = "Deprecado (D-432): usar GET /discount-definitions?type=COHORT_PROMOTION. Se retira cuando el front use el contrato nuevo.")
    @GetMapping("/promotions")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PromotionResponse> findActive(@RequestParam(defaultValue = "active") String status) {
        return listUseCase.listActivePromotions();
    }

    @GetMapping("/reports/billing/promotions/lost-benefits")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<LostPromotionBenefitResponse> lostBenefits() {
        return lostBenefitsUseCase.findLostBenefits();
    }
}
