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
import com.red_escuela.backend.dto.CoursePricingResponse;
import com.red_escuela.backend.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.dto.ProgramCohortResponse;
import com.red_escuela.backend.dto.ProgramPricingResponse;
import com.red_escuela.backend.dto.PromotionResponse;
import com.red_escuela.backend.entity.FeeScheduleConcept;
import com.red_escuela.backend.entity.Person;
import com.red_escuela.backend.exception.FeeScheduleNotFoundException;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

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
    public FeeScheduleResponse createFeeSchedule(@Valid @RequestBody CreateFeeScheduleRequest request) {
        return FeeScheduleResponse.from(feeScheduleService.create(request, actor()));
    }

    @GetMapping("/fee-schedules")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<FeeScheduleResponse> listFeeSchedules() {
        return feeScheduleService.list().stream().map(FeeScheduleResponse::from).toList();
    }

    @PostMapping("/late-fee-policies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public LateFeePolicyResponse createLateFeePolicy(@Valid @RequestBody CreateLateFeePolicyRequest request) {
        return LateFeePolicyResponse.from(lateFeePolicyService.create(request, actor()));
    }

    @GetMapping("/late-fee-policies")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<LateFeePolicyResponse> listLateFeePolicies() {
        return lateFeePolicyService.list().stream().map(LateFeePolicyResponse::from).toList();
    }

    @PostMapping("/billing-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public BillingPeriodResponse createBillingPeriod(@Valid @RequestBody CreateBillingPeriodRequest request) {
        return BillingPeriodResponse.from(billingPeriodService.create(request, actor()));
    }

    @GetMapping("/billing-periods")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<BillingPeriodResponse> listBillingPeriods() {
        return billingPeriodService.list().stream().map(BillingPeriodResponse::from).toList();
    }

    @PostMapping("/students/{id}/discounts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public DiscountResponse createDiscount(@PathVariable Long id, @Valid @RequestBody CreateDiscountRequest request) {
        return DiscountResponse.from(discountService.create(id, request, actor()));
    }

    @GetMapping("/students/{id}/discounts")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<DiscountResponse> listDiscounts(@PathVariable Long id) {
        return discountService.listByStudent(id).stream().map(DiscountResponse::from).toList();
    }

    @PostMapping("/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createPromotion(@Valid @RequestBody CreatePromotionRequest request) {
        return PromotionResponse.from(promotionService.create(request, actor()));
    }

    @GetMapping("/promotions")
    @PreAuthorize("hasRole('DIRECTOR') or hasRole('ADMINISTRATIVO')")
    public List<PromotionResponse> listPromotions() {
        return promotionService.list().stream().map(PromotionResponse::from).toList();
    }

    @PostMapping("/program-cohorts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public ProgramCohortResponse createProgramCohort(@Valid @RequestBody CreateProgramCohortRequest request) {
        return ProgramCohortResponse.from(programCohortService.create(request));
    }

    // Sin @PreAuthorize a propósito (mismo criterio que /me/...): cualquier alumno
    // tiene que poder ver las cohortes disponibles de una carrera antes de
    // inscribirse — no es información sensible, solo fechas y orden.
    @GetMapping("/programs/{id}/cohorts")
    public List<ProgramCohortResponse> listProgramCohorts(@PathVariable Long id) {
        return programCohortService.listByProgram(id).stream().map(ProgramCohortResponse::from).toList();
    }

    // Consulta pública de precios (sin @PreAuthorize): el alumno necesita saber
    // cuánto pagar ANTES de transferir y upload el comprobante — separado del CRUD
    // de FeeSchedule (Director-only). Épica 7 Fase 3.
    @GetMapping("/programs/{id}/pricing")
    public ProgramPricingResponse pricingDePrograma(@PathVariable Long id) {
        LocalDate hoy = LocalDate.now();
        BigDecimal matriculaCohorte1 = montoOSiNoHayFeeSchedule(() -> feeScheduleService.montoMatriculaVigente(id, 1, hoy));
        BigDecimal matriculaCohorte2 = montoOSiNoHayFeeSchedule(() -> feeScheduleService.montoMatriculaVigente(id, 2, hoy));
        BigDecimal cuotaMensual = montoOSiNoHayFeeSchedule(
                () -> feeScheduleService.vigenteParaPrograma(id, FeeScheduleConcept.CUOTA_MENSUAL, hoy).getAmount());
        return new ProgramPricingResponse(id, matriculaCohorte1, matriculaCohorte2, cuotaMensual);
    }

    @GetMapping("/courses/{id}/pricing")
    public CoursePricingResponse pricingDeCurso(@PathVariable Long id) {
        LocalDate hoy = LocalDate.now();
        BigDecimal cuotaMensual = montoOSiNoHayFeeSchedule(
                () -> feeScheduleService.vigenteParaCurso(id, FeeScheduleConcept.CUOTA_MENSUAL, hoy).getAmount());
        return new CoursePricingResponse(id, cuotaMensual);
    }

    // El Director puede no haber configurado un precio todavía — la pantalla de
    // detalle tiene que poder mostrar "sin definir" en vez de un 404 que rompa
    // toda la carga de la pantalla por un solo concepto faltante.
    private BigDecimal montoOSiNoHayFeeSchedule(Supplier<BigDecimal> resolver) {
        try {
            return resolver.get();
        } catch (FeeScheduleNotFoundException e) {
            return null;
        }
    }

    private Person actor() {
        return currentPersonService.resolveCurrentPerson();
    }
}
