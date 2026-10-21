package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptProtectionFacts;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-154")
@Tag("D-186")
class ReceiptUploadedByDueDatePolicyTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);
    private final ReceiptUploadedByDueDatePolicy policy = new ReceiptUploadedByDueDatePolicy();

    private PolicyVerdict evaluate(InvoicePaymentStatus status, LocalDate upload, LocalDate transfer) {
        return policy.evaluateRule(new ReceiptProtectionCheck(new ReceiptProtectionFacts(status, DUE, upload, transfer)));
    }

    @Test
    void protectsAPendingInvoiceUploadedAndTransferredUpToTheDueDateInclusive() {
        assertThat(evaluate(InvoicePaymentStatus.PENDING, DUE, DUE).allowed()).isTrue();
        assertThat(evaluate(InvoicePaymentStatus.PENDING, DUE.minusDays(1), DUE.minusDays(2)).allowed()).isTrue();
    }

    @Test
    void doesNotProtectAnInvoiceThatIsNotPending() {
        assertThat(evaluate(InvoicePaymentStatus.OVERDUE, DUE.minusDays(1), DUE.minusDays(1)).code()).isEqualTo("INVOICE_ALREADY_OVERDUE");
        assertThat(evaluate(InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, DUE.minusDays(1), DUE.minusDays(1)).code()).isEqualTo("INVOICE_ALREADY_OVERDUE");
    }

    @Test
    void doesNotProtectAReceiptUploadedAfterTheDueDate() {
        assertThat(evaluate(InvoicePaymentStatus.PENDING, DUE.plusDays(1), DUE.minusDays(1)).code()).isEqualTo("RECEIPT_UPLOADED_AFTER_DUE_DATE");
    }

    @Test
    void doesNotProtectATransferAfterTheDueDate() {
        assertThat(evaluate(InvoicePaymentStatus.PENDING, DUE, DUE.plusDays(1)).code()).isEqualTo("TRANSFER_AFTER_DUE_DATE");
    }
}
