package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class LateFeeOnInvoiceSettledListener {
    private static final String HANDLER_NAME = "LateFeeOnInvoiceSettledListener";

    private final LateFeeAccrualService accrualService;
    private final ProcessedEffectGuard effectGuard;

    public LateFeeOnInvoiceSettledListener(LateFeeAccrualService accrualService, ProcessedEffectGuard effectGuard) {
        this.accrualService = accrualService;
        this.effectGuard = effectGuard;
    }

    @ApplicationModuleListener
    public void reconcileLateFeesOfSettledInvoice(InvoicePaidDomainEvent event) {
        if (!InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name().equals(event.cause())) {
            return;
        }
        effectGuard.runEffectOnce(event, HANDLER_NAME, () -> accrualService.reconcileLateFeesOfSettledInvoice(event.invoiceId(), issueDateOf(event)));
    }

    private static LocalDate issueDateOf(InvoicePaidDomainEvent event) {
        LocalDate today = ApplicationTime.today();
        return event.businessDate().isBefore(today) ? today : event.businessDate();
    }
}
