package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationRequest;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationResponse;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceSimulationUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/monthly-invoice-runs/simulations")
@PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
public class MonthlyInvoiceSimulationController {
    private final MonthlyInvoiceSimulationUseCase simulationUseCase;

    public MonthlyInvoiceSimulationController(MonthlyInvoiceSimulationUseCase simulationUseCase) {
        this.simulationUseCase = simulationUseCase;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MonthlyInvoiceSimulationResponse simulateMonthlyInvoices(@Valid @RequestBody MonthlyInvoiceSimulationRequest request) {
        return simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(request.period());
    }
}
