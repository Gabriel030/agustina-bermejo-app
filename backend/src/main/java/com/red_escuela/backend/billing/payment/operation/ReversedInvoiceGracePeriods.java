package com.red_escuela.backend.billing.payment.operation;

import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class ReversedInvoiceGracePeriods {
    private static final Logger log = LoggerFactory.getLogger(ReversedInvoiceGracePeriods.class);

    private final BillingConfigurationReader configurationReader;
    private final InvoiceAllocationQueryService invoiceQueries;

    ReversedInvoiceGracePeriods(BillingConfigurationReader configurationReader, InvoiceAllocationQueryService invoiceQueries) {
        this.configurationReader = configurationReader;
        this.invoiceQueries = invoiceQueries;
    }

    int gracePeriodDaysOf(Long invoiceId) {
        Invoice invoice = invoiceQueries.findInvoicesByIds(List.of(invoiceId)).get(0);
        return configurationReader.findLatePaymentSettingsEffectiveOn(invoice.getDueDate()).or(configurationReader::findFirstLatePaymentSettings)
                .map(LatePaymentTerms::gracePeriodDays)
                .orElseGet(() -> {
                    log.warn("Factura id={} sin versión de mora aplicable: se revierte su imputación sin días de gracia", invoice.getId());
                    return 0;
                });
    }
}
