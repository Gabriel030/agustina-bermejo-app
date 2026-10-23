package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.usecase.ClosePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.CreatePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.FindLostCohortPromotionBenefitsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListActivePromotionsByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;
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

    @PostMapping("/courses/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return createUseCase.createForCourse(id, request);
    }

    @PostMapping("/programs/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForProgram(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return createUseCase.createForProgram(id, request);
    }

    @PostMapping("/promotions/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void close(@PathVariable Long id) {
        closeUseCase.closePromotion(id);
    }

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
