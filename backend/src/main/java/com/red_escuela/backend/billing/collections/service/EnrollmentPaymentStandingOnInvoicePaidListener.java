package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class EnrollmentPaymentStandingOnInvoicePaidListener {
    private static final Logger log = LoggerFactory.getLogger(EnrollmentPaymentStandingOnInvoicePaidListener.class);

    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentPaymentStandingService standingService;

    public EnrollmentPaymentStandingOnInvoicePaidListener(EnrollmentDebtFactsQuery factsQuery, EnrollmentPaymentStandingService standingService) {
        this.factsQuery = factsQuery;
        this.standingService = standingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void reevaluateStandingOfPaidInvoice(InvoicePaidDomainEvent event) {
        for (EnrollmentKind kind : EnrollmentKind.values()) {
            for (Long enrollmentId : factsQuery.findEnrollmentIdsOfInvoice(kind, event.invoiceId())) {
                try {
                    standingService.reevaluateExistingStanding(kind, enrollmentId, event.businessDate());
                } catch (RuntimeException failure) {
                    log.warn("No se pudo reevaluar el estado de cobranza al pagarse una factura: tipo={}, inscripcion={}, factura={}", kind, enrollmentId,
                            event.invoiceId(), failure);
                }
            }
        }
    }
}
