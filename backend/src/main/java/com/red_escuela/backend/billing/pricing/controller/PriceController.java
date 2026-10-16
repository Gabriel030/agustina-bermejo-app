package com.red_escuela.backend.billing.pricing.controller;

import com.red_escuela.backend.billing.pricing.dto.CancelPriceVersionRequest;
import com.red_escuela.backend.billing.pricing.dto.ChangePriceRequest;
import com.red_escuela.backend.billing.pricing.dto.PriceChangeValidationResponse;
import com.red_escuela.backend.billing.pricing.dto.PriceVersionResponse;
import com.red_escuela.backend.billing.pricing.usecase.CancelScheduledPriceVersionUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ProposePriceChangeUseCase;
import com.red_escuela.backend.billing.pricing.usecase.ValidatePriceChangeUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PriceController {
    private final ProposePriceChangeUseCase proposePriceChangeUseCase;
    private final ValidatePriceChangeUseCase validatePriceChangeUseCase;
    private final CancelScheduledPriceVersionUseCase cancelScheduledPriceVersionUseCase;

    public PriceController(ProposePriceChangeUseCase proposePriceChangeUseCase, ValidatePriceChangeUseCase validatePriceChangeUseCase,
                           CancelScheduledPriceVersionUseCase cancelScheduledPriceVersionUseCase) {
        this.proposePriceChangeUseCase = proposePriceChangeUseCase;
        this.validatePriceChangeUseCase = validatePriceChangeUseCase;
        this.cancelScheduledPriceVersionUseCase = cancelScheduledPriceVersionUseCase;
    }

    @PostMapping("/prices/{priceId}/versions")
    @PreAuthorize("hasRole('DIRECTOR')")
    public ResponseEntity<PriceVersionResponse> publishPriceVersion(@PathVariable Long priceId, @Valid @RequestBody ChangePriceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PriceVersionResponse.from(
                proposePriceChangeUseCase.publishPriceVersion(priceId, request.amount(), request.discountOrNull(), request.effectiveFrom(), request.changeReason())));
    }

    @PostMapping("/prices/{priceId}/versions/validation")
    @PreAuthorize("hasRole('DIRECTOR')")
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
