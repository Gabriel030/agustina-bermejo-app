package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationRequest;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationResponse;
import com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceSimulationUseCase;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/monthly-invoice-runs/simulations")
public class MonthlyInvoiceSimulationController {
    private final MonthlyInvoiceSimulationUseCase simulationUseCase;

    public MonthlyInvoiceSimulationController(MonthlyInvoiceSimulationUseCase simulationUseCase) {
        this.simulationUseCase = simulationUseCase;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "billing.simulate_monthly_run", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public MonthlyInvoiceSimulationResponse simulateMonthlyInvoices(@Valid @RequestBody MonthlyInvoiceSimulationRequest request) {
        return simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(request.period());
    }
}
