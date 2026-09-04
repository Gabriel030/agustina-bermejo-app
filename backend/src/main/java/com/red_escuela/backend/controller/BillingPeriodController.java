package com.red_escuela.backend.controller;

import com.red_escuela.backend.dto.BillingPeriodResponse;
import com.red_escuela.backend.dto.CreateBillingPeriodRequest;
import com.red_escuela.backend.service.BillingPeriodService;
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

    private final BillingPeriodService billingPeriodService;

    public BillingPeriodController(BillingPeriodService billingPeriodService) {
        this.billingPeriodService = billingPeriodService;
    }

    @PostMapping("/billing-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('DIRECTOR')")
    public BillingPeriodResponse crear(@Valid @RequestBody CreateBillingPeriodRequest request) {
        return BillingPeriodResponse.from(billingPeriodService.crear(request));
    }

    @GetMapping("/billing-periods/current")
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public BillingPeriodResponse current() {
        var current = billingPeriodService.current();
        return current == null ? null : BillingPeriodResponse.from(current);
    }
}
