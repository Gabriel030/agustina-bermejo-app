package com.red_escuela.backend.billing.collections.service;

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
    private final ProcessedEffectGuard effectGuard;

    public LateFeeCorrectionOnPaymentReviewDecidedListener(LateFeeWithdrawalCorrectionService correctionService, ProcessedEffectGuard effectGuard) {
        this.correctionService = correctionService;
        this.effectGuard = effectGuard;
    }

    @ApplicationModuleListener
    public void retryCorrectionOfDebitNoteWhoseReceiptWasApproved(InvoicePaidDomainEvent event) {
        if (!InvoicePaymentEvent.FULL_PAYMENT_ALLOCATED.name().equals(event.cause())) {
            return;
        }
        effectGuard.runEffectOnce(event, APPROVED_HANDLER_NAME, () -> correctionService.correctLateFeeOfDebitNote(event.invoiceId(), ApplicationTime.today()));
    }

    @ApplicationModuleListener
    public void retryCorrectionOfDebitNoteWhoseReceiptWasRejected(InvoicePaymentReviewReleasedEvent event) {
        correctionService.correctLateFeeOfDebitNote(event.invoiceId(), ApplicationTime.today());
    }
}
