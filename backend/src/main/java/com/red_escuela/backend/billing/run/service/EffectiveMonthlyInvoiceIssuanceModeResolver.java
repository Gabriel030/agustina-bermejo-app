package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import org.springframework.stereotype.Component;

@Component
public class EffectiveMonthlyInvoiceIssuanceModeResolver {

    public MonthlyInvoiceIssuanceMode resolveModeOfNewRun() {
        return MonthlyInvoiceIssuanceMode.DIRECT;
    }
}
