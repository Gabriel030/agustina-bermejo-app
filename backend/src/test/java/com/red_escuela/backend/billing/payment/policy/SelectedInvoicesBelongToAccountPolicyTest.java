package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.ACCOUNT;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.ars;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.invoice;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.pending;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-097")
@Tag("D-153")
class SelectedInvoicesBelongToAccountPolicyTest {
    private static final LocalDate DUE = LocalDate.of(2027, 3, 10);
    private final SelectedInvoicesBelongToAccountPolicy policy = new SelectedInvoicesBelongToAccountPolicy();

    private static BankTransferReceiptRegistrationCheck check(ReceiptInvoiceFact... selected) {
        return new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(selected), List.of(selected), false);
    }

    @Test
    void rejectsInvoiceOfAnotherAccount() {
        ReceiptInvoiceFact foreign = new ReceiptInvoiceFact(1L, ACCOUNT + 1, Currency.ARS, true, InvoicePaymentStatus.PENDING, DUE, ars("100.00"));

        assertThat(policy.evaluateRule(check(foreign)).code()).isEqualTo("INVOICE_OF_ANOTHER_ACCOUNT");
    }

    @Test
    void rejectsInvoiceOfAnotherCurrency() {
        ReceiptInvoiceFact usd = new ReceiptInvoiceFact(1L, ACCOUNT, Currency.USD, true, InvoicePaymentStatus.PENDING, DUE, ars("100.00"));

        assertThat(policy.evaluateRule(check(usd)).code()).isEqualTo("INVOICE_CURRENCY_MISMATCH");
    }

    @Test
    void rejectsPaidCancelledOrDraftInvoice() {
        ReceiptInvoiceFact paid = invoice(1, InvoicePaymentStatus.PAID, DUE, "100.00");
        ReceiptInvoiceFact cancelled = invoice(2, InvoicePaymentStatus.CANCELLED, DUE, "100.00");
        ReceiptInvoiceFact draft = new ReceiptInvoiceFact(3L, ACCOUNT, Currency.ARS, false, null, null, ars("100.00"));

        assertThat(policy.evaluateRule(check(paid)).code()).isEqualTo("INVOICE_NOT_PAYABLE_BY_RECEIPT");
        assertThat(policy.evaluateRule(check(cancelled)).code()).isEqualTo("INVOICE_NOT_PAYABLE_BY_RECEIPT");
        assertThat(policy.evaluateRule(check(draft)).code()).isEqualTo("INVOICE_NOT_PAYABLE_BY_RECEIPT");
    }

    @Test
    void acceptsPendingOverdueAndUnderReviewInvoicesOfTheAccount() {
        ReceiptInvoiceFact overdue = invoice(2, InvoicePaymentStatus.OVERDUE, DUE, "100.00");
        ReceiptInvoiceFact underReview = invoice(3, InvoicePaymentStatus.PAYMENT_UNDER_REVIEW, DUE, "100.00");

        assertThat(policy.evaluateRule(check(pending(1, DUE, "100.00"), overdue, underReview)).allowed()).isTrue();
    }
}
