package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.configuration.domain.IssuanceMode;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceIssuanceMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class EffectiveMonthlyInvoiceIssuanceModeResolver {
    private static final Logger log = LoggerFactory.getLogger(EffectiveMonthlyInvoiceIssuanceModeResolver.class);

    private final BillingConfigurationReader configurationReader;

    public EffectiveMonthlyInvoiceIssuanceModeResolver(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    public MonthlyInvoiceIssuanceMode resolveModeEffectiveOn(LocalDate businessDate) {
        return configurationReader.findIssuanceSettingsEffectiveOn(businessDate)
                .map(settings -> settings.issuanceMode() == IssuanceMode.REVIEW ? MonthlyInvoiceIssuanceMode.REVIEW : MonthlyInvoiceIssuanceMode.DIRECT)
                .orElseGet(() -> {
                    log.error("No hay configuración de emisión vigente el {}: la corrida mensual emite en modo DIRECT", businessDate);
                    return MonthlyInvoiceIssuanceMode.DIRECT;
                });
    }
}
