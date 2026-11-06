package com.red_escuela.backend.billing.payment.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.payment.domain.ReceiptInvoiceFact;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-155")
@Tag("D-154")
@Tag("D-138")
class AnnualUpfrontReceiptPoliciesTest {
    private static final LocalDateTime DEADLINE = LocalDateTime.of(2027, 3, 4, 10, 0);

    private static Money ars(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    private static ReceiptInvoiceFact annual() {
        return new ReceiptInvoiceFact(1L, 7L, Currency.ARS, false, InvoicePaymentStatus.PENDING, null, ars("800000.00"), false, true, DEADLINE);
    }

    private static ReceiptInvoiceFact monthly() {
        return new ReceiptInvoiceFact(2L, 7L, Currency.ARS, true, InvoicePaymentStatus.PENDING, LocalDate.of(2027, 3, 10), ars("80000.00"), false);
    }

    private static AnnualUpfrontReceiptCheck check(List<ReceiptInvoiceFact> selected, String amount, LocalDateTime startedAt, LocalDate transferDate) {
        return new AnnualUpfrontReceiptCheck(selected, false, false, ars(amount), startedAt, transferDate);
    }

    @Test
    void aReceiptUploadedOnTheDeadlineInstantWithATransferOfThatDayCounts() {
        AnnualUpfrontReceiptCheck onTime = check(List.of(annual()), "800000.00", DEADLINE, LocalDate.of(2027, 3, 4));

        assertThat(new AnnualUpfrontReceiptWithinPaymentDeadlinePolicy().evaluateRule(onTime).allowed()).isTrue();
    }

    @Test
    void aReceiptUploadedAfterTheDeadlineInstantIsLate() {
        AnnualUpfrontReceiptCheck late = check(List.of(annual()), "800000.00", DEADLINE.plusSeconds(1), LocalDate.of(2027, 3, 3));

        assertThat(new AnnualUpfrontReceiptWithinPaymentDeadlinePolicy().evaluateRule(late).code()).isEqualTo("ANNUAL_UPFRONT_RECEIPT_LATE");
    }

    @Test
    void aTransferDatedAfterTheDeadlineDayIsLateEvenIfUploadedInTime() {
        AnnualUpfrontReceiptCheck late = check(List.of(annual()), "800000.00", DEADLINE.minusHours(1), LocalDate.of(2027, 3, 5));

        assertThat(new AnnualUpfrontReceiptWithinPaymentDeadlinePolicy().evaluateRule(late).code()).isEqualTo("ANNUAL_UPFRONT_RECEIPT_LATE");
    }

    @Test
    void anAmountDifferentFromTheTotalIsRejectedAndTheExactTotalIsAccepted() {
        assertThat(new AnnualUpfrontReceiptPaysExactTotalPolicy().evaluateRule(check(List.of(annual()), "799999.99", DEADLINE, LocalDate.of(2027, 3, 4))).allowed()).isFalse();
        assertThat(new AnnualUpfrontReceiptPaysExactTotalPolicy().evaluateRule(check(List.of(annual()), "800000.00", DEADLINE, LocalDate.of(2027, 3, 4))).allowed()).isTrue();
    }

    @Test
    void theAnnualReceiptCannotBeMixedWithAMonthlyInvoice() {
        AnnualUpfrontReceiptCheck mixed = check(List.of(annual(), monthly()), "880000.00", DEADLINE, LocalDate.of(2027, 3, 4));

        assertThat(new AnnualUpfrontReceiptPaysOnlyTheAnnualInvoicePolicy().evaluateRule(mixed).code()).isEqualTo("ANNUAL_UPFRONT_RECEIPT_MUST_BE_ALONE");
    }

    @Test
    void aDiscardedRequestIsExpiredAndAReviewedOneIsAlreadyInitiated() {
        AnnualUpfrontReceiptCheck discarded = new AnnualUpfrontReceiptCheck(List.of(), true, false, ars("1.00"), DEADLINE, DEADLINE.toLocalDate());
        AnnualUpfrontReceiptCheck reviewed = new AnnualUpfrontReceiptCheck(List.of(), false, true, ars("1.00"), DEADLINE, DEADLINE.toLocalDate());

        assertThat(new AnnualUpfrontPaymentNotExpiredPolicy().evaluateRule(discarded).code()).isEqualTo("ANNUAL_UPFRONT_PAYMENT_EXPIRED");
        assertThat(new AnnualUpfrontPaymentNotAlreadyInitiatedPolicy().evaluateRule(reviewed).code()).isEqualTo("ANNUAL_UPFRONT_PAYMENT_ALREADY_INITIATED");
    }
}
