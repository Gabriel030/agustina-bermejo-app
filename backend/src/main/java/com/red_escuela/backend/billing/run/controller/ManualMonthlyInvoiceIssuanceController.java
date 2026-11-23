package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.run.dto.ManualMonthlyInvoiceIssuanceRequest;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceRunResponse;
import com.red_escuela.backend.billing.run.usecase.ManualMonthlyInvoiceIssuanceUseCase;
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
@RequestMapping("/monthly-invoice-runs/manual-issuances")
public class ManualMonthlyInvoiceIssuanceController {
    private final ManualMonthlyInvoiceIssuanceUseCase issuanceUseCase;

    public ManualMonthlyInvoiceIssuanceController(ManualMonthlyInvoiceIssuanceUseCase issuanceUseCase) {
        this.issuanceUseCase = issuanceUseCase;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequiresPermission(code = "billing.issue_monthly_invoices_manually", legacyRoles = "ROLE_DIRECTOR")
    public MonthlyInvoiceRunResponse issueMonthlyInvoicesManually(@Valid @RequestBody ManualMonthlyInvoiceIssuanceRequest request) {
        return issuanceUseCase.issueMonthlyInvoicesManuallyAsOfToday(request.period(), request.simulationRunId());
    }
}
