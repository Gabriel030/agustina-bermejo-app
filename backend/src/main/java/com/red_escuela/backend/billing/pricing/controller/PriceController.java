package com.red_escuela.backend.billing.pricing.controller;

import com.red_escuela.backend.billing.pricing.dto.CancelPriceVersionRequest;
import com.red_escuela.backend.billing.pricing.dto.ChangePriceRequest;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.dto.PriceChangeImpactResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceChangePendingAuthorizationResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceChangeValidationResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceSummaryResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceVersionHistoryEntryResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceVersionResponse;
import com.red_escuela.backend.billing.pricing.usecase.CancelScheduledPriceVersionUseCase;
import com.red_escuela.backend.billing.pricing.usecase.GetPriceChangeImpactUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ListPriceVersionHistoryUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ListPricesUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ProposePriceChangeUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ValidatePriceChangeUseCase;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PriceController {
    private final ProposePriceChangeUseCase proposePriceChangeUseCase;
    private final ValidatePriceChangeUseCase validatePriceChangeUseCase;
    private final CancelScheduledPriceVersionUseCase cancelScheduledPriceVersionUseCase;
    private final ListPricesUseCase listPricesUseCase;
    private final ListPriceVersionHistoryUseCase listPriceVersionHistoryUseCase;
    private final GetPriceChangeImpactUseCase getPriceChangeImpactUseCase;

    public PriceController(ProposePriceChangeUseCase proposePriceChangeUseCase, ValidatePriceChangeUseCase validatePriceChangeUseCase,
                           CancelScheduledPriceVersionUseCase cancelScheduledPriceVersionUseCase, ListPricesUseCase listPricesUseCase,
                           ListPriceVersionHistoryUseCase listPriceVersionHistoryUseCase, GetPriceChangeImpactUseCase getPriceChangeImpactUseCase) {
        this.proposePriceChangeUseCase = proposePriceChangeUseCase;
        this.validatePriceChangeUseCase = validatePriceChangeUseCase;
        this.cancelScheduledPriceVersionUseCase = cancelScheduledPriceVersionUseCase;
        this.listPricesUseCase = listPricesUseCase;
        this.listPriceVersionHistoryUseCase = listPriceVersionHistoryUseCase;
        this.getPriceChangeImpactUseCase = getPriceChangeImpactUseCase;
    }

    @GetMapping("/prices")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PriceSummaryResponse> listPrices(@RequestParam(required = false) Long programId, @RequestParam(required = false) Long courseId) {
        return listPricesUseCase.listPrices(programId, courseId);
    }

    @GetMapping("/prices/{priceId}/versions")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public List<PriceVersionHistoryEntryResponse> listPriceVersionHistory(@PathVariable Long priceId) {
        return listPriceVersionHistoryUseCase.listPriceVersionHistory(priceId);
    }

    @PostMapping("/prices/{priceId}/versions/impact")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public PriceChangeImpactResponse getPriceChangeImpact(@PathVariable Long priceId, @Valid @RequestBody ChangePriceRequest request) {
        return PriceChangeImpactResponse.from(getPriceChangeImpactUseCase.getPriceChangeImpact(priceId, request.amount(), request.discountOrNull(), request.effectiveFrom()));
    }

    @PostMapping("/prices/{priceId}/versions")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = PriceVersionResponse.class))),
            @ApiResponse(responseCode = "202", description = "Accepted", content = @Content(schema = @Schema(implementation = PriceChangePendingAuthorizationResponse.class)))})
    public ResponseEntity<?> publishPriceVersion(@PathVariable Long priceId, @Valid @RequestBody ChangePriceRequest request) {
        BillingOperationOutcome<PriceVersion> outcome = proposePriceChangeUseCase.proposeOrPublishPriceVersion(priceId, request.amount(), request.discountOrNull(),
                request.effectiveFrom(), request.changeReason());
        return switch (outcome) {
            case BillingOperationOutcome.Processed<PriceVersion> processed -> ResponseEntity.status(HttpStatus.CREATED).body(PriceVersionResponse.from(processed.result()));
            case BillingOperationOutcome.AwaitingAuthorization<PriceVersion> awaiting ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(new PriceChangePendingAuthorizationResponse(awaiting.authorizationRequestId()));
        };
    }

    @PostMapping("/prices/{priceId}/versions/validation")
    @PreAuthorize("hasAnyRole('DIRECTOR', 'ADMINISTRATIVO')")
    public PriceChangeValidationResponse validatePriceChange(@PathVariable Long priceId, @Valid @RequestBody ChangePriceRequest request) {
        return new PriceChangeValidationResponse(validatePriceChangeUseCase.validatePriceChange(priceId, request.amount(), request.discountOrNull(),
                request.effectiveFrom(), request.changeReason()));
    }

    @PostMapping("/price-versions/{priceVersionId}/cancellation")
    @PreAuthorize("hasRole('DIRECTOR')")
    public PriceVersionResponse cancelScheduledPriceVersion(@PathVariable Long priceVersionId, @Valid @RequestBody CancelPriceVersionRequest request) {
        return PriceVersionResponse.from(cancelScheduledPriceVersionUseCase.cancelScheduledVersion(priceVersionId, request.reason()));
    }
}
