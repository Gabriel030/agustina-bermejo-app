package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.billing.dto.BillingPeriodResponse;
import com.red_escuela.backend.billing.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.billing.usecase.billingperiod.CreateBillingPeriodUseCase;
import com.red_escuela.backend.billing.usecase.billingperiod.GetCurrentBillingPeriodUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BillingPeriodController {

    private final CreateBillingPeriodUseCase createBillingPeriodUseCase;
    private final GetCurrentBillingPeriodUseCase getCurrentBillingPeriodUseCase;

    public BillingPeriodController(CreateBillingPeriodUseCase createBillingPeriodUseCase, GetCurrentBillingPeriodUseCase getCurrentBillingPeriodUseCase) {
        this.createBillingPeriodUseCase = createBillingPeriodUseCase;
        this.getCurrentBillingPeriodUseCase = getCurrentBillingPeriodUseCase;
    }

    @PostMapping("/billing-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public BillingPeriodResponse crear(@Valid @RequestBody CreateBillingPeriodRequest request) {
        return BillingPeriodResponse.from(createBillingPeriodUseCase.create(request));
    }

    @GetMapping("/billing-periods/current")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BillingPeriodResponse current() {
        var current = getCurrentBillingPeriodUseCase.getCurrent();
        return current == null ? null : BillingPeriodResponse.from(current);
    }
}
