package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.billing.dto.PromotionResponse;
import com.red_escuela.backend.billing.usecase.promotion.ClosePromotionUseCase;
import com.red_escuela.backend.billing.usecase.promotion.CreatePromotionForCourseUseCase;
import com.red_escuela.backend.billing.usecase.promotion.CreatePromotionForProgramUseCase;
import com.red_escuela.backend.billing.usecase.promotion.FindActivePromotionsUseCase;
import com.red_escuela.backend.billing.usecase.promotion.FindLostPromotionBenefitsUseCase;
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

    private final CreatePromotionForCourseUseCase createPromotionForCourseUseCase;
    private final CreatePromotionForProgramUseCase createPromotionForProgramUseCase;
    private final ClosePromotionUseCase closePromotionUseCase;
    private final FindActivePromotionsUseCase findActivePromotionsUseCase;
    private final FindLostPromotionBenefitsUseCase findLostPromotionBenefitsUseCase;

    public PromotionController(CreatePromotionForCourseUseCase createPromotionForCourseUseCase,
                                CreatePromotionForProgramUseCase createPromotionForProgramUseCase,
                                ClosePromotionUseCase closePromotionUseCase, FindActivePromotionsUseCase findActivePromotionsUseCase,
                                FindLostPromotionBenefitsUseCase findLostPromotionBenefitsUseCase) {
        this.createPromotionForCourseUseCase = createPromotionForCourseUseCase;
        this.createPromotionForProgramUseCase = createPromotionForProgramUseCase;
        this.closePromotionUseCase = closePromotionUseCase;
        this.findActivePromotionsUseCase = findActivePromotionsUseCase;
        this.findLostPromotionBenefitsUseCase = findLostPromotionBenefitsUseCase;
    }

    @PostMapping("/courses/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(createPromotionForCourseUseCase.create(id, request));
    }

    @PostMapping("/programs/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForProgram(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(createPromotionForProgramUseCase.create(id, request));
    }

    @PostMapping("/promotions/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void close(@PathVariable Long id) {
        closePromotionUseCase.close(id);
    }

    // status=active es el único filtro con caso de uso real hoy (elegir a qué promociones
    // vigentes atar una campaña) — la lista histórica completa no tiene consumidor todavía.
    @GetMapping("/promotions")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PromotionResponse> findActive(@RequestParam(defaultValue = "active") String status) {
        return findActivePromotionsUseCase.findActive().stream().map(PromotionResponse::from).toList();
    }

    @GetMapping("/reports/billing/promotions/lost-benefits")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<LostPromotionBenefitResponse> lostBenefits() {
        return findLostPromotionBenefitsUseCase.find();
    }
}
