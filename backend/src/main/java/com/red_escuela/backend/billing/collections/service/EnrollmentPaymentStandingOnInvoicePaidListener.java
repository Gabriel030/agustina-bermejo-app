package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.repository.EnrollmentDebtFactsQuery;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentPaymentStandingOnInvoicePaidListener {
    private final EnrollmentDebtFactsQuery factsQuery;
    private final EnrollmentPaymentStandingService standingService;

    public EnrollmentPaymentStandingOnInvoicePaidListener(EnrollmentDebtFactsQuery factsQuery, EnrollmentPaymentStandingService standingService) {
        this.factsQuery = factsQuery;
        this.standingService = standingService;
    }

    @EventListener
    public void reevaluateStandingOfPaidInvoice(InvoicePaidDomainEvent event) {
        for (EnrollmentKind kind : EnrollmentKind.values()) {
            for (Long enrollmentId : factsQuery.findEnrollmentIdsOfInvoice(kind, event.invoiceId())) {
                standingService.reevaluateExistingStanding(kind, enrollmentId, event.businessDate());
            }
        }
    }
}
