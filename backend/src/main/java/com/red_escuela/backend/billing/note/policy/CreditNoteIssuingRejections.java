package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.exception.CreditNoteExceedsCreditableAmountException;
import com.red_escuela.backend.billing.note.exception.CreditNoteDistributionMismatchException;
import com.red_escuela.backend.billing.note.exception.CreditNoteIssuingRejectedException;
import com.red_escuela.backend.billing.note.exception.CreditNoteJustificationRequiredException;
import com.red_escuela.backend.billing.note.exception.CreditNoteLinesInvalidException;
import com.red_escuela.backend.billing.note.exception.CreditNoteRefundExceedsPaidAmountException;
import com.red_escuela.backend.billing.note.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class CreditNoteIssuingRejections {

    private CreditNoteIssuingRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        return switch (violations.get(0).code()) {
            case "CREDIT_NOTE_EXCEEDS_CREDITABLE_AMOUNT" -> new CreditNoteExceedsCreditableAmountException();
            case "CREDIT_NOTE_REFUND_EXCEEDS_PAID_AMOUNT" -> new CreditNoteRefundExceedsPaidAmountException(violations);
            case "PENDING_BANK_TRANSFER_ON_INVOICE" -> new PendingBankTransferOnInvoiceException(violations);
            case "CREDIT_NOTE_DISTRIBUTION_MISMATCH" -> new CreditNoteDistributionMismatchException(violations);
            case "CREDIT_NOTE_JUSTIFICATION_REQUIRED" -> new CreditNoteJustificationRequiredException(violations);
            case "CREDIT_NOTE_LINES_INVALID" -> new CreditNoteLinesInvalidException(violations);
            case "INVALID_CREDIT_NOTE_REASON" -> new InvalidCreditNoteReasonException(violations);
            default -> new CreditNoteIssuingRejectedException(violations);
        };
    }
}
