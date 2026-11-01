package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.shared.module.RequiresModule;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaymentReviewReleasedEvent;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class LateFeeCorrectionOnPaymentReviewDecidedListener {
    private static final String APPROVED_HANDLER_NAME = "LateFeeCorrectionOnPaymentReviewDecidedListener.approved";

    private final LateFeeWithdrawalCorrectionService correctionService;
    private final LateFeeSettlementCorrectionService settlementCorrectionService;
    private final ProcessedEffectGuard effectGuard;

    public LateFeeCorrectionOnPaymentReviewDecidedListener(LateFeeWithdrawalCorrectionService correctionService, LateFeeSettlementCorrectionService settlementCorrectionService,
                                                           ProcessedEffectGuard effectGuard) {
        this.correctionService = correctionService;
        this.settlementCorrectionService = settlementCorrectionService;
        this.effectGuard = effectGuard;
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @ApplicationModuleListener
    public void retryCorrectionOfDebitNoteWhoseReceiptWasApproved(InvoicePaidDomainEvent event) {
        if (!InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name().equals(event.cause())) {
            return;
        }
        effectGuard.runEffectOnce(event, APPROVED_HANDLER_NAME, () -> correctLateFeesOfDebitNote(event.invoiceId()));
    }

    @RequiresModule(value = RequiresModule.BILLING, minMode = BillingMode.FULL)
    @ApplicationModuleListener
    public void retryCorrectionOfDebitNoteWhoseReceiptWasRejected(InvoicePaymentReviewReleasedEvent event) {
        correctLateFeesOfDebitNote(event.invoiceId());
    }

    private void correctLateFeesOfDebitNote(Long debitNoteId) {
        correctionService.correctLateFeeOfDebitNote(debitNoteId, ApplicationTime.today());
        settlementCorrectionService.correctLateFeeOfDebitNote(debitNoteId);
    }
}
