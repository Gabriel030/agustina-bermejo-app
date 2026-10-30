package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.dto.ManualMonthlyInvoiceIssuanceRequest;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.usecase.ManualMonthlyInvoiceIssuanceUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monthly-invoice-runs/manual-issuances")
@PreAuthorize("hasRole('DIRECTOR')")
public class ManualMonthlyInvoiceIssuanceController {
    private final ManualMonthlyInvoiceIssuanceUseCase issuanceUseCase;

    public ManualMonthlyInvoiceIssuanceController(ManualMonthlyInvoiceIssuanceUseCase issuanceUseCase) {
        this.issuanceUseCase = issuanceUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonthlyInvoiceRunResponse issueMonthlyInvoicesManually(@Valid @RequestBody ManualMonthlyInvoiceIssuanceRequest request) {
        return issuanceUseCase.issueMonthlyInvoicesManuallyAsOfToday(request.period(), request.simulationRunId());
    }
}
