package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.LateFeeCorrectionCreditNoteKind;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.policy.CancellationInvoiceNotUnderReviewPolicy;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationContext;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationRejections;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class LateFeeAnnulmentOnInstallmentCancellation {
    private static final Logger log = LoggerFactory.getLogger(LateFeeAnnulmentOnInstallmentCancellation.class);
    private static final CancellationInvoiceNotUnderReviewPolicy NOT_UNDER_REVIEW = new CancellationInvoiceNotUnderReviewPolicy();

    private final LateFeeDebitNoteLookup debitNoteLookup;
    private final InvoiceCreditAccessService invoices;
    private final LateFeeCorrectionCreditNoteIssuer creditNoteIssuer;
    private final Clock clock;

    public LateFeeAnnulmentOnInstallmentCancellation(LateFeeDebitNoteLookup debitNoteLookup, InvoiceCreditAccessService invoices,
                                                     LateFeeCorrectionCreditNoteIssuer creditNoteIssuer, Clock clock) {
        this.debitNoteLookup = debitNoteLookup;
        this.invoices = invoices;
        this.creditNoteIssuer = creditNoteIssuer;
        this.clock = clock;
    }

    public void requireNoLateFeeDebitNoteUnderPaymentReview(CancelInvoiceCommand command, Long installmentId) {
        for (Long debitNoteId : debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(installmentId)) {
            Invoice debitNote = invoices.findInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId));
            requireNotUnderPaymentReview(command, debitNote);
        }
    }

    public List<CreditNote> annulLiveLateFeesOfInstallment(CancelInvoiceCommand command, Invoice lockedInstallment) {
        List<Invoice> lockedDebitNotes = new ArrayList<>();
        for (Long debitNoteId : debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(lockedInstallment.getId())) {
            lockedDebitNotes.add(invoices.lockInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId)));
        }
        lockedDebitNotes.forEach(debitNote -> requireNotUnderPaymentReview(command, debitNote));
        LocalDate businessDate = LocalDate.now(clock);
        List<CreditNote> notes = new ArrayList<>();
        for (Invoice debitNote : lockedDebitNotes) {
            NonNegativeMoney remaining = debitNote.getPrimaryLine().calculateCreditableAmount();
            if (debitNote.getPaymentStatus() == InvoicePaymentStatus.CANCELLED || remaining.value().isZero()) {
                continue;
            }
            String debitNoteNumber = debitNote.getInvoiceNumber().value();
            String installmentNumber = lockedInstallment.getInvoiceNumber().value();
            CreditNote note = creditNoteIssuer.issueCorrectionCreditNote(debitNote, remaining,
                    "Anulación del recargo por mora " + debitNoteNumber + " por la cancelación de la cuota " + installmentNumber, businessDate,
                    LateFeeCorrectionCreditNoteKind.AFTER_INSTALLMENT_CANCELLATION);
            log.info("Recargo anulado por la cancelación de su cuota: cuota={}, notaDeDébito={}, notaDeCrédito={}, monto={}, pagado={}", installmentNumber,
                    debitNoteNumber, note.getCreditNoteNumber().value(), note.getTotalAmount().value().amount(), note.getFreeCreditAmount().value().amount());
            notes.add(note);
        }
        return notes;
    }

    private static void requireNotUnderPaymentReview(CancelInvoiceCommand command, Invoice debitNote) {
        InvoiceCancellationContext context = new InvoiceCancellationContext(command, CreditedInvoiceFacts.of(debitNote));
        new PolicyEvaluationResult(List.of(NOT_UNDER_REVIEW.evaluateRule(context)))
                .throwIfDenied(violations -> InvoiceCancellationRejections.rejectionOf(violations, debitNote.getPaymentStatus()));
    }
}
