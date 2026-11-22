package com.red_escuela.backend.billing.run.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.run.dto.BillingRunAlertResponse;
import com.red_escuela.backend.billing.run.usecase.BillingRunAlertQueryUseCase;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
@RequestMapping("/billing-run-alerts")
public class BillingRunAlertController {
    private final BillingRunAlertQueryUseCase queryUseCase;

    public BillingRunAlertController(BillingRunAlertQueryUseCase queryUseCase) {
        this.queryUseCase = queryUseCase;
    }

    @GetMapping
    @RequiresPermission(code = "billing.read_billing_runs", legacyRoles = "ROLE_ADMINISTRATIVO,ROLE_DIRECTOR")
    public List<BillingRunAlertResponse> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return queryUseCase.listBillingRunAlerts(from, to);
    }
}
