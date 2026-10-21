package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.ACCOUNT;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.overdue;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.pending;
import static com.red_escuela.backend.billing.payment.policy.ReceiptInvoiceFacts.underReview;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-153")
@Tag("D-260")
class ReceiptCoversOldestInvoicesFirstPolicyTest {
    private static final LocalDate MARCH = LocalDate.of(2027, 3, 10);
    private static final LocalDate APRIL = LocalDate.of(2027, 4, 10);
    private static final LocalDate MAY = LocalDate.of(2027, 5, 10);
    private final ReceiptCoversOldestInvoicesFirstPolicy policy = new ReceiptCoversOldestInvoicesFirstPolicy();

    private static BankTransferReceiptRegistrationCheck check(List<ReceiptInvoiceFact> open, ReceiptInvoiceFact... selected) {
        return new BankTransferReceiptRegistrationCheck(ACCOUNT, Currency.ARS, List.of(selected), open, false);
    }

    @Test
    void mustChooseOldestFirst() {
        ReceiptInvoiceFact march = overdue(1, MARCH, "80000.00");
        ReceiptInvoiceFact april = pending(2, APRIL, "80000.00");
        ReceiptInvoiceFact may = pending(3, MAY, "80000.00");
        List<ReceiptInvoiceFact> open = List.of(may, march, april);

        PolicyVerdict skippingTheOldest = policy.evaluateRule(check(open, april));
        PolicyVerdict skippingTheMiddle = policy.evaluateRule(check(open, march, may));
        PolicyVerdict oldestOnly = policy.evaluateRule(check(open, march));
        PolicyVerdict oldestTwo = policy.evaluateRule(check(open, april, march));
        PolicyVerdict all = policy.evaluateRule(check(open, march, april, may));

        assertThat(skippingTheOldest.code()).isEqualTo("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST");
        assertThat(skippingTheMiddle.code()).isEqualTo("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST");
        assertThat(oldestOnly.allowed()).isTrue();
        assertThat(oldestTwo.allowed()).isTrue();
        assertThat(all.allowed()).isTrue();
    }

    @Test
    void sameDueDateIsOrderedByInvoiceId() {
        ReceiptInvoiceFact first = pending(1, MARCH, "80000.00");
        ReceiptInvoiceFact second = pending(2, MARCH, "80000.00");

        assertThat(policy.evaluateRule(check(List.of(second, first), first)).allowed()).isTrue();
        assertThat(policy.evaluateRule(check(List.of(second, first), second)).code()).isEqualTo("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST");
    }

    @Test
    void skipsInvoicesUnderReviewByAnotherPendingReceipt() {
        ReceiptInvoiceFact march = underReview(1, MARCH, "80000.00");
        ReceiptInvoiceFact april = pending(2, APRIL, "80000.00");
        ReceiptInvoiceFact may = pending(3, MAY, "80000.00");
        List<ReceiptInvoiceFact> open = List.of(march, april, may);

        assertThat(policy.evaluateRule(check(open, april)).allowed()).isTrue();
        assertThat(policy.evaluateRule(check(open, april, may)).allowed()).isTrue();
        assertThat(policy.evaluateRule(check(open, may)).code()).isEqualTo("RECEIPT_MUST_COVER_OLDEST_INVOICES_FIRST");
    }

    @Test
    void rejectsAnInvoiceAlreadyUnderReviewByAnotherReceipt() {
        ReceiptInvoiceFact march = underReview(1, MARCH, "80000.00");
        ReceiptInvoiceFact april = pending(2, APRIL, "80000.00");
        List<ReceiptInvoiceFact> open = List.of(march, april);

        assertThat(policy.evaluateRule(check(open, march)).code()).isEqualTo("INVOICE_ALREADY_UNDER_REVIEW");
        assertThat(policy.evaluateRule(check(open, march, april)).code()).isEqualTo("INVOICE_ALREADY_UNDER_REVIEW");
    }

    @Test
    void allowsAnOverdueInvoiceAlreadyIncludedInAnotherPendingReceipt() {
        ReceiptInvoiceFact march = overdue(1, MARCH, "80000.00");
        ReceiptInvoiceFact april = pending(2, APRIL, "80000.00");

        PolicyVerdict verdict = policy.evaluateRule(check(List.of(march, april), march));

        assertThat(verdict.allowed()).isTrue();
    }

    @Test
    void anInvoiceThatIsNotOpenDoesNotCountAsSelectable() {
        ReceiptInvoiceFact notIssued = new ReceiptInvoiceFact(1L, ACCOUNT, Currency.ARS, false, null, MARCH, ReceiptInvoiceFacts.ars("1.00"));
        ReceiptInvoiceFact april = pending(2, APRIL, "80000.00");

        assertThat(policy.evaluateRule(check(List.of(notIssued, april), april)).allowed()).isTrue();
    }
}
