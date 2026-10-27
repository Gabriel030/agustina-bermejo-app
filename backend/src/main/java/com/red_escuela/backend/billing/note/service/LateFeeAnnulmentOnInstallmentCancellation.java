package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.LateFeeCorrectionCreditNoteKind;
import com.red_escuela.backend.billing.note.domain.LiveLateFeeToAnnul;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.policy.LateFeeAnnulmentRejections;
import com.red_escuela.backend.billing.note.policy.LateFeeDebitNoteAnnulmentContext;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
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

    private final LateFeeDebitNoteLookup debitNoteLookup;
    private final InvoiceCreditAccessService invoices;
    private final LateFeeCorrectionCreditNoteIssuer creditNoteIssuer;
    private final PolicySet<LateFeeDebitNoteAnnulmentContext> annulmentPolicies;
    private final Clock clock;

    public LateFeeAnnulmentOnInstallmentCancellation(LateFeeDebitNoteLookup debitNoteLookup, InvoiceCreditAccessService invoices,
                                                     LateFeeCorrectionCreditNoteIssuer creditNoteIssuer, PolicySet<LateFeeDebitNoteAnnulmentContext> annulmentPolicies, Clock clock) {
        this.debitNoteLookup = debitNoteLookup;
        this.invoices = invoices;
        this.creditNoteIssuer = creditNoteIssuer;
        this.annulmentPolicies = annulmentPolicies;
        this.clock = clock;
    }

    public void requireNoLateFeeDebitNoteUnderPaymentReview(Long installmentId) {
        for (Long debitNoteId : debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(installmentId)) {
            Invoice debitNote = invoices.findInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId));
            requireNotUnderPaymentReview(debitNote);
        }
    }

    public List<CreditNote> annulLiveLateFeesOfInstallment(Invoice lockedInstallment) {
        List<Invoice> lockedDebitNotes = new ArrayList<>();
        for (Long debitNoteId : debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(lockedInstallment.getId())) {
            lockedDebitNotes.add(invoices.lockInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId)));
        }
        lockedDebitNotes.forEach(this::requireNotUnderPaymentReview);
        LocalDate businessDate = LocalDate.now(clock);
        List<CreditNote> notes = new ArrayList<>();
        for (LiveLateFeeToAnnul liveLateFee : LiveLateFeeToAnnul.amongDebitNotes(lockedDebitNotes)) {
            Invoice debitNote = liveLateFee.debitNote();
            NonNegativeMoney remaining = liveLateFee.amountToAnnul();
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

    public boolean hasLateFeeDebitNoteUnderPaymentReview(Long installmentId) {
        return debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(installmentId).stream()
                .map(debitNoteId -> invoices.findInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId)))
                .anyMatch(debitNote -> debitNote.getPaymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW);
    }

    public List<LiveLateFeeToAnnul> describeLiveLateFeesOf(Long installmentId) {
        return LiveLateFeeToAnnul.amongDebitNotes(findDebitNotesOf(installmentId));
    }

    public List<PolicyViolation> findPaymentReviewViolationsOf(Long installmentId) {
        return findDebitNotesOf(installmentId).stream().flatMap(debitNote -> evaluateNotUnderPaymentReview(debitNote).violations().stream()).toList();
    }

    private List<Invoice> findDebitNotesOf(Long installmentId) {
        return debitNoteLookup.findDebitNoteIdsOfOriginInvoiceInAscendingOrder(installmentId).stream()
                .map(debitNoteId -> invoices.findInvoice(debitNoteId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(debitNoteId))).toList();
    }

    private void requireNotUnderPaymentReview(Invoice debitNote) {
        evaluateNotUnderPaymentReview(debitNote).throwIfDenied(LateFeeAnnulmentRejections::rejectionOf);
    }

    private PolicyEvaluationResult evaluateNotUnderPaymentReview(Invoice debitNote) {
        return annulmentPolicies.evaluateRulesUntilFirstViolation(new LateFeeDebitNoteAnnulmentContext(debitNote.getId(), debitNote.getInvoiceNumber().value(), debitNote.getPaymentStatus()));
    }
}
