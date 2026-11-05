package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.run.domain.FailedInvoiceRetrySchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class FailedInvoiceRetryScheduleResolver {
    private static final Logger log = LoggerFactory.getLogger(FailedInvoiceRetryScheduleResolver.class);

    private final BillingConfigurationReader configurationReader;

    public FailedInvoiceRetryScheduleResolver(BillingConfigurationReader configurationReader) {
        this.configurationReader = configurationReader;
    }

    public FailedInvoiceRetrySchedule resolveScheduleEffectiveOn(LocalDate date) {
        return configurationReader.findIssuanceSettingsEffectiveOn(date).map(FailedInvoiceRetrySchedule::from).orElseGet(() -> {
            log.error("No hay configuración de emisión vigente el {}: los ítems fallidos no se reintentan", date);
            return FailedInvoiceRetrySchedule.withoutRetries();
        });
    }
}
