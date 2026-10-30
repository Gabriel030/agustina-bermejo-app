package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPaymentStandingOnInvoicePaidListener {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingOnInvoicePaidListener.class);

    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentPaymentStandingService standingService;

    public EnrollmentPaymentStandingOnInvoicePaidListener(EnrollmentDebtFactsQuery factsQuery, EnrollmentPaymentStandingService standingService) {
        this.factsQuery = factsQuery;
        this.standingService = standingService;
    }

    @ApplicationModuleListener
    public void reevaluateStandingOfPaidInvoice(InvoicePaidDomainEvent event) {
        int failures = 0;
        for (EnrollmentKind kind : EnrollmentKind.values()) {
            for (Long enrollmentId : factsQuery.findEnrollmentIdsOfInvoice(kind, event.invoiceId())) {
                try {
                    standingService.reevaluateExistingStanding(kind, enrollmentId, event.businessDate());
                } catch (RuntimeException failure) {
                    failures++;
                    log.warn("No se pudo reevaluar el estado de cobranza al pagarse una factura: tipo={}, inscripcion={}, factura={}", kind, enrollmentId,
                            event.invoiceId(), failure);
                }
            }
        }
        if (failures > 0) {
            throw new IllegalStateException("The payment standing of " + failures + " enrollment(s) could not be reevaluated for the paid invoice " + event.invoiceId());
        }
    }
}
