package com.red_escuela.backend.billing.note.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.invoice.exception.IllegalInvoicePaymentStatusTransitionException;
import com.red_escuela.backend.billing.note.exception.CreditNoteDirectorApprovalRequiredException;
import com.red_escuela.backend.billing.note.exception.CreditNoteIssuingRejectedException;
import com.red_escuela.backend.billing.note.exception.CreditNoteJustificationRequiredException;
import com.red_escuela.backend.billing.note.exception.InvalidCreditNoteReasonException;
import com.red_escuela.backend.billing.note.exception.PendingBankTransferOnInvoiceException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class InvoiceCancellationRejections {

    private InvoiceCancellationRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations, InvoicePaymentStatus currentStatus) {
        return switch (violations.get(0).code()) {
            case "CREDIT_NOTE_JUSTIFICATION_REQUIRED" -> new CreditNoteJustificationRequiredException(violations);
            case "INVALID_CREDIT_NOTE_REASON" -> new InvalidCreditNoteReasonException(violations);
            case "PENDING_BANK_TRANSFER_ON_INVOICE" -> new PendingBankTransferOnInvoiceException(violations);
            case "CREDIT_NOTE_DIRECTOR_APPROVAL_REQUIRED" -> new CreditNoteDirectorApprovalRequiredException(violations);
            case "ILLEGAL_INVOICE_PAYMENT_STATUS_TRANSITION" ->
                    new IllegalInvoicePaymentStatusTransitionException(currentStatus, InvoicePaymentEvent.CANCELLATION_CREDIT_NOTE_APPLIED);
            default -> new CreditNoteIssuingRejectedException(violations);
        };
    }
}
