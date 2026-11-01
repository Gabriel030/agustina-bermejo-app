package com.red_escuela.backend.billing.discount.controller;

import com.red_escuela.backend.billing.discount.domain.LostBenefitFilter;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitsSlice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import com.red_escuela.backend.billing.discount.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.discount.dto.PromotionResponse;
import com.red_escuela.backend.billing.discount.usecase.ClosePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.CreatePromotionByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.usecase.FindLostCohortPromotionBenefitsUseCase;
import com.red_escuela.backend.billing.discount.usecase.ListActivePromotionsByLegacyContractUseCase;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.shared.module.RequiresModule;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

import java.time.YearMonth;
import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class PromotionController {
    public static final String TOTAL_COUNT_HEADER = "X-Total-Count";

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

    @Operation(deprecated = true, description = "Deprecado (D-432): usar POST /discount-definitions con tipo COHORT_PROMOTION. Responde 400 INVALID_DISCOUNT_DEFINITION si algún beneficio es de EXAM_FEE, DEGREE_ISSUANCE_FEE o ADMINISTRATIVE_FEE (D-433). Se retira cuando el front use el contrato nuevo.")
    @PostMapping("/courses/{id}/promotions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public PromotionResponse createForCourse(@PathVariable Long id, @Valid @RequestBody CreatePromotionRequest request) {
        return createUseCase.createForCourse(id, request);
    }

    @Operation(deprecated = true, description = "Deprecado (D-432): usar POST /discount-definitions con tipo COHORT_PROMOTION. Responde 400 INVALID_DISCOUNT_DEFINITION si algún beneficio es de EXAM_FEE, DEGREE_ISSUANCE_FEE o ADMINISTRATIVE_FEE (D-433). Se retira cuando el front use el contrato nuevo.")
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
    @ApiResponse(responseCode = "200", description = "Beneficios perdidos; el total sin paginar viaja en el header X-Total-Count",
            headers = @Header(name = "X-Total-Count", description = "Cantidad total de beneficios perdidos que cumplen los filtros, sin paginar", schema = @Schema(type = "integer", format = "int64")))
    public ResponseEntity<List<LostPromotionBenefitResponse>> lostBenefits(@RequestParam(required = false) Long discountDefinitionId,
                                                                          @RequestParam(required = false) InvoiceType feeType,
                                                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth cutOffFrom,
                                                                          @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth cutOffTo,
                                                                          @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
        LostPromotionBenefitsSlice slice = lostBenefitsUseCase.findLostBenefits(new LostBenefitFilter(discountDefinitionId, feeType, cutOffFrom, cutOffTo), page, size);
        return ResponseEntity.ok().header(TOTAL_COUNT_HEADER, Long.toString(slice.totalElements())).body(slice.items());
    }
}
