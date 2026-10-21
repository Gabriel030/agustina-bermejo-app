package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.exception.BankTransferDetailsRequiredException;
import com.red_escuela.backend.billing.payment.exception.InvoiceAlreadyUnderReviewException;
import com.red_escuela.backend.billing.payment.exception.PossibleDuplicateBankTransferException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceCurrencyMismatchException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceNotPayableException;
import com.red_escuela.backend.billing.payment.exception.ReceiptInvoiceOfAnotherAccountException;
import com.red_escuela.backend.billing.payment.exception.ReceiptMustCoverOldestInvoicesFirstException;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.util.List;

public final class BankTransferReceiptRejections {
    private static final String DETAILS_CODE_PREFIX = "BANK_TRANSFER_";
    private static final PolicyViolation DUPLICATE_BY_UNIQUE_INDEX = new PolicyViolation("POSSIBLE_DUPLICATE_BANK_TRANSFER",
            "Ya hay un comprobante con el mismo número de operación, fecha y monto.");

    private BankTransferReceiptRejections() {
    }

    public static RuntimeException rejectionOf(List<PolicyViolation> violations) {
        String code = violations.get(0).code();
        if (code.startsWith(DETAILS_CODE_PREFIX)) {
            return new BankTransferDetailsRequiredException(violations);
        }
        return switch (code) {
            case "INVOICE_CURRENCY_MISMATCH" -> new ReceiptInvoiceCurrencyMismatchException(violations);
            case "INVOICE_OF_ANOTHER_ACCOUNT" -> new ReceiptInvoiceOfAnotherAccountException(violations);
            case "INVOICE_NOT_PAYABLE_BY_RECEIPT" -> new ReceiptInvoiceNotPayableException(violations);
            case "INVOICE_ALREADY_UNDER_REVIEW" -> new InvoiceAlreadyUnderReviewException(violations);
            case "RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST" -> new ReceiptMustCoverOldestInvoicesFirstException(violations);
            case "POSSIBLE_DUPLICATE_BANK_TRANSFER" -> new PossibleDuplicateBankTransferException(violations);
            default -> throw new IllegalStateException("Unmapped bank transfer receipt violation " + code);
        };
    }

    public static RuntimeException duplicateDetectedByUniqueIndex() {
        return new PossibleDuplicateBankTransferException(List.of(DUPLICATE_BY_UNIQUE_INDEX));
    }
}
