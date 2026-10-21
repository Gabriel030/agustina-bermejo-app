package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Percentage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-035")
@Tag("D-036")
@Tag("D-054")
@Tag("D-161")
@Tag("D-145")
class InvoiceIssuingTimelineIT extends AbstractInvoiceIssuingIT {

    private BigDecimal expectedDebt(List<BigDecimal> totals) {
        return totals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Test
    void monthlyIssuingAcrossTheYearKeepsAllInvariantsAfterEveryMonth() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var plain = factory.newLearner(offering, null);
        var scholar = factory.newLearner(offering, null);
        var promoted = factory.newLearner(offering, null);
        factory.grantScholarship(offering, scholar, Percentage.ofPoints(new BigDecimal("50")), YearMonth.of(2027, 3), YearMonth.of(2027, 4));
        factory.redeemPromotion(offering, promoted, FeeType.MONTHLY_INSTALLMENT, Percentage.ofPoints(new BigDecimal("25")), 3);
        List<BigDecimal> plainTotals = new ArrayList<>();
        List<BigDecimal> scholarTotals = new ArrayList<>();
        List<BigDecimal> promotedTotals = new ArrayList<>();

        for (int month = 3; month <= 8; month++) {
            LocalDate firstOfMonth = LocalDate.of(2027, month, 1);
            today(firstOfMonth);
            if (month == 5) {
                factory.newPrice(offering, FeeType.MONTHLY_INSTALLMENT, 100000, firstOfMonth);
            }
            for (var entry : List.of(java.util.Map.entry(plain, plainTotals), java.util.Map.entry(scholar, scholarTotals), java.util.Map.entry(promoted, promotedTotals))) {
                InvoiceIssuingRequest request = installment(offering, entry.getKey(), YearMonth.of(2027, month), firstOfMonth.plusDays(10), firstOfMonth);
                var invoice = issued(runner.issueInvoiceInNewTransaction(request));
                entry.getValue().add(invoice.getAmounts().total().value().amount());
                assertThat(runner.issueInvoiceInNewTransaction(request)).isInstanceOf(InvoiceIssuingResult.InvoiceAlreadyIssued.class);
            }
            invariants.assertAllBillingInvariantsHold();
            assertThat(accountOf(plain.studentId(), Currency.ARS).getBalance().amount().value().amount()).isEqualByComparingTo(expectedDebt(plainTotals));
            assertThat(accountOf(scholar.studentId(), Currency.ARS).getBalance().amount().value().amount()).isEqualByComparingTo(expectedDebt(scholarTotals));
            assertThat(accountOf(promoted.studentId(), Currency.ARS).getBalance().amount().value().amount()).isEqualByComparingTo(expectedDebt(promotedTotals));
        }

        assertThat(plainTotals).usingElementComparator(BigDecimal::compareTo).containsExactly(
                new BigDecimal("80000"), new BigDecimal("80000"), new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"));
        assertThat(scholarTotals).usingElementComparator(BigDecimal::compareTo).containsExactly(
                new BigDecimal("40000"), new BigDecimal("40000"), new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"));
        assertThat(promotedTotals).usingElementComparator(BigDecimal::compareTo).containsExactly(
                new BigDecimal("60000"), new BigDecimal("60000"), new BigDecimal("75000"), new BigDecimal("100000"), new BigDecimal("100000"), new BigDecimal("100000"));
        assertThat(jdbc.queryForObject("select count(*) from invoice_line where program_enrollment_id = ?", Long.class, plain.programEnrollmentId())).isEqualTo(6L);
        assertThat(entriesOf(plain.studentId(), Currency.ARS)).hasSize(6);
    }

    @Test
    void aRetryOfAPastMonthKeepsTheOriginalRunPricingDate() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var first = factory.newLearner(offering, null);
        var retried = factory.newLearner(offering, null);
        today(LocalDate.of(2027, 3, 1));
        issued(runner.issueInvoiceInNewTransaction(installment(offering, first, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));
        factory.newPrice(offering, FeeType.MONTHLY_INSTALLMENT, 95000, LocalDate.of(2027, 3, 2));
        today(LocalDate.of(2027, 3, 4));

        var late = issued(runner.issueInvoiceInNewTransaction(installment(offering, retried, YearMonth.of(2027, 3), LocalDate.of(2027, 3, 11), LocalDate.of(2027, 3, 1))));

        assertThat(late.getAmounts().total().value().amount()).isEqualByComparingTo("80000");
        assertThat(late.getIssueDate()).isEqualTo(LocalDate.of(2027, 3, 4));
        invariants.assertAllBillingInvariantsHold();
    }
}
