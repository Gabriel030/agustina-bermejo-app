package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.CreatePromotionRequest;
import com.red_escuela.backend.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.dto.PromotionResponse;
import com.red_escuela.backend.service.PromotionService;
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

    private final PromotionService promotionService;

    public PromotionController(PromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/courses/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(promotionService.createForCourse(id, request));
    }

    @PostMapping("/programs/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForProgram(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(promotionService.createForProgram(id, request));
    }

    @PostMapping("/promotions/{id}/close")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('DIRECTOR')")
    public void close(@PathVariable Long id) {
        promotionService.close(id);
    }

    // status=active es el único filtro con caso de uso real hoy (elegir a qué promociones
    // vigentes atar una campaña) — la lista histórica completa no tiene consumidor todavía.
    @GetMapping("/promotions")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<PromotionResponse> findActive(@RequestParam(defaultValue = "active") String status) {
        return promotionService.findActive().stream().map(PromotionResponse::from).toList();
    }

    @GetMapping("/reports/billing/promotions/lost-benefits")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<LostPromotionBenefitResponse> lostBenefits() {
        return promotionService.findLostBenefits();
    }
}
