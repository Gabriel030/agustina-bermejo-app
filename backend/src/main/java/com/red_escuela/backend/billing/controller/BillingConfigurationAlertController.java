package com.red_escuela.backend.billing.controller;

import com.red_escuela.backend.shared.permission.RequiresPermission;
import com.red_escuela.backend.billing.dto.BillingConfigurationAlertResponse;
import com.red_escuela.backend.billing.service.BillingConfigurationAlertService;
import com.red_escuela.backend.shared.module.RequiresModule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiresModule(RequiresModule.BILLING)
public class BillingConfigurationAlertController {
    private final BillingConfigurationAlertService alertService;

    public BillingConfigurationAlertController(BillingConfigurationAlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping("/billing/configuration-alerts")
    @RequiresPermission(code = "billing.read_billing_runs", legacyRoles = "ROLE_DIRECTOR,ROLE_ADMINISTRATIVO")
    public List<BillingConfigurationAlertResponse> list() {
        return alertService.listOpen().stream().map(BillingConfigurationAlertResponse::from).toList();
    }
}
