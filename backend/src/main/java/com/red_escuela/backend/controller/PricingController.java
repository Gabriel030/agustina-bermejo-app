package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.BillingPeriodResponse;
import com.red_escuela.backend.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.dto.CreateDiscountRequest;
import com.red_escuela.backend.dto.CreateFeeScheduleRequest;
import com.red_escuela.backend.dto.CreateLateFeePolicyRequest;
import com.red_escuela.backend.dto.CreatePromotionRequest;
import com.red_escuela.backend.dto.DiscountResponse;
import com.red_escuela.backend.dto.FeeScheduleResponse;
import com.red_escuela.backend.dto.LateFeePolicyResponse;
import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.dto.ProgramCohortResponse;
import com.red_escuela.backend.dto.PromotionResponse;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.service.BillingPeriodService;
import com.red_escuela.backend.service.CurrentPersonService;
import com.red_escuela.backend.service.DiscountService;
import com.red_escuela.backend.service.FeeScheduleService;
import com.red_escuela.backend.service.LateFeePolicyService;
import com.red_escuela.backend.service.ProgramCohortService;
import com.red_escuela.backend.service.PromotionService;
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

/**
 * Precios, mora, ventana de emisión, becas y promociones — todo alta/edición es
 * exclusivo del Director (regla de negocio explícita, ver spec funcional de
 * Épica 8 Fase 1). Un solo controller para las 5 entidades: son CRUDs chicos
 * relacionados (todos "reglas que el Director define"), separarlos en 5
 * controllers de un puñado de líneas cada uno no agrega claridad.
 */
@RestController
public class PricingController {

    private final FeeScheduleService feeScheduleService;
    private final LateFeePolicyService lateFeePolicyService;
    private final BillingPeriodService billingPeriodService;
    private final DiscountService discountService;
    private final PromotionService promotionService;
    private final ProgramCohortService programCohortService;
    private final CurrentPersonService currentPersonService;

    public PricingController(
            FeeScheduleService feeScheduleService, LateFeePolicyService lateFeePolicyService,
            BillingPeriodService billingPeriodService, DiscountService discountService,
            PromotionService promotionService, ProgramCohortService programCohortService,
            CurrentPersonService currentPersonService) {
        this.feeScheduleService = feeScheduleService;
        this.lateFeePolicyService = lateFeePolicyService;
        this.billingPeriodService = billingPeriodService;
        this.discountService = discountService;
        this.promotionService = promotionService;
        this.programCohortService = programCohortService;
        this.currentPersonService = currentPersonService;
    }

    @PostMapping("/fee-schedules")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public FeeScheduleResponse crearFeeSchedule(@Valid @RequestBody CreateFeeScheduleRequest request) {
        return FeeScheduleResponse.from(feeScheduleService.crear(request, actor()));
    }

    @GetMapping("/fee-schedules")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<FeeScheduleResponse> listarFeeSchedules() {
        return feeScheduleService.listar().stream().map(FeeScheduleResponse::from).toList();
    }

    @PostMapping("/late-fee-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public LateFeePolicyResponse crearLateFeePolicy(@Valid @RequestBody CreateLateFeePolicyRequest request) {
        return LateFeePolicyResponse.from(lateFeePolicyService.crear(request, actor()));
    }

    @GetMapping("/late-fee-policies")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<LateFeePolicyResponse> listarLateFeePolicies() {
        return lateFeePolicyService.listar().stream().map(LateFeePolicyResponse::from).toList();
    }

    @PostMapping("/billing-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public BillingPeriodResponse crearBillingPeriod(@Valid @RequestBody CreateBillingPeriodRequest request) {
        return BillingPeriodResponse.from(billingPeriodService.crear(request, actor()));
    }

    @GetMapping("/billing-periods")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<BillingPeriodResponse> listarBillingPeriods() {
        return billingPeriodService.listar().stream().map(BillingPeriodResponse::from).toList();
    }

    @PostMapping("/students/{id}/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public DiscountResponse crearDiscount(@PathVariable Long id, @Valid @RequestBody CreateDiscountRequest request) {
        return DiscountResponse.from(discountService.crear(id, request, actor()));
    }

    @GetMapping("/students/{id}/discounts")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<DiscountResponse> listarDiscounts(@PathVariable Long id) {
        return discountService.listarPorAlumno(id).stream().map(DiscountResponse::from).toList();
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse crearPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(promotionService.crear(request, actor()));
    }

    @GetMapping("/promotions")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<PromotionResponse> listarPromotions() {
        return promotionService.listar().stream().map(PromotionResponse::from).toList();
    }

    @PostMapping("/program-cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramCohortResponse crearProgramCohort(@Valid @RequestBody CreateProgramCohortRequest request) {
        return ProgramCohortResponse.from(programCohortService.crear(request));
    }

    @GetMapping("/programs/{id}/cohorts")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<ProgramCohortResponse> listarProgramCohorts(@PathVariable Long id) {
        return programCohortService.listarPorPrograma(id).stream().map(ProgramCohortResponse::from).toList();
    }

    private Person actor() {
        return currentPersonService.resolveCurrentPerson();
    }
}
