package com.red_escuela.backend.billing.collections.policy;

import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceIsPastDueDatePolicyTest {
    private final InvoiceIsPastDueDatePolicy policy = new InvoiceIsPastDueDatePolicy();

    private boolean allows(InvoicePaymentStatus status, String dueDate, int grace, String today) {
        return policy.evaluateRule(new OverdueDetectionContext(status, LocalDate.parse(dueDate), grace, LocalDate.parse(today))).allowed();
    }

    @Test
    @Tag("D-057")
    @Tag("D-288")
    void aPendingInvoiceDueOnThe12thWithFiveGraceDaysIsOverdueOnlyOnThe18th() {
        assertThat(allows(InvoicePaymentStatus.PENDING, "2027-03-12", 5, "2027-03-17")).isFalse();
        assertThat(allows(InvoicePaymentStatus.PENDING, "2027-03-12", 5, "2027-03-18")).isTrue();
    }

    @ParameterizedTest
    @Tag("D-288")
    @CsvSource({
            "2027-03-12, 0, 2027-03-12, false",
            "2027-03-12, 0, 2027-03-13, true",
            "2028-02-28, 1, 2028-02-29, false",
            "2028-02-28, 1, 2028-03-01, true",
            "2027-02-28, 1, 2027-03-01, false",
            "2027-02-28, 1, 2027-03-02, true",
            "2027-12-31, 5, 2028-01-05, false",
            "2027-12-31, 5, 2028-01-06, true",
            "2027-01-31, 30, 2027-03-02, false",
            "2027-01-31, 30, 2027-03-03, true",
            "2028-01-31, 29, 2028-02-29, false",
            "2028-01-31, 29, 2028-03-01, true"
    })
    void theLastDayWithoutOverdueIsTheDueDateMoreTheGraceAcrossMonthLeapYearAndYearBoundaries(String dueDate, int grace, String today, boolean expected) {
        assertThat(allows(InvoicePaymentStatus.PENDING, dueDate, grace, today)).isEqualTo(expected);
    }

    @ParameterizedTest
    @Tag("D-153")
    @EnumSource(value = InvoicePaymentStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void onlyAPendingInvoiceCanBecomeOverdueNeverOneUnderPaymentReview(InvoicePaymentStatus status) {
        assertThat(allows(status, "2020-01-01", 0, "2027-03-18")).isFalse();
    }

    @Test
    @Tag("D-288")
    void aDenialCarriesItsViolationCode() {
        var verdict = policy.evaluateRule(new OverdueDetectionContext(InvoicePaymentStatus.PENDING, LocalDate.parse("2027-03-12"), 5, LocalDate.parse("2027-03-17")));
        assertThat(verdict.allowed()).isFalse();
        assertThat(verdict.policy()).isEqualTo("InvoiceIsPastDueDatePolicy");
        assertThat(verdict.code()).isEqualTo("INVOICE_NOT_PAST_DUE_DATE");
    }
}
