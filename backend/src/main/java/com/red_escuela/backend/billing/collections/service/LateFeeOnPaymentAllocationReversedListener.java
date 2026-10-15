package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.events.PaymentAllocationReversedEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class LateFeeOnPaymentAllocationReversedListener {

    private final LateFeeAccrualService accrualService;

    public LateFeeOnPaymentAllocationReversedListener(LateFeeAccrualService accrualService) {
        this.accrualService = accrualService;
    }

    @ApplicationModuleListener
    public void reconcileLateFeesOfInvoiceWithReversedPayment(PaymentAllocationReversedEvent event) {
        accrualService.reconcileLateFeesOfInvoice(event.invoiceId(), event.businessDate());
    }
}
