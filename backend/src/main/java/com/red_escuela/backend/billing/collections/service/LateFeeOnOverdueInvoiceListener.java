package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.event.InvoiceMarkedAsOverdueEvent;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class LateFeeOnOverdueInvoiceListener {

    private final LateFeeAccrualService accrualService;

    public LateFeeOnOverdueInvoiceListener(LateFeeAccrualService accrualService) {
        this.accrualService = accrualService;
    }

    @ApplicationModuleListener
    public void reconcileLateFeesOfOverdueInvoice(InvoiceMarkedAsOverdueEvent event) {
        accrualService.reconcileLateFeesOfInvoice(event.invoiceId(), event.businessDate());
    }
}
