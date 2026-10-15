package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.domain.CurrentAccountEntry;
import com.red_escuela.backend.billing.invariant.BillingInvariantChecker;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import io.micrometer.core.instrument.MeterRegistry;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SlowTest(reason = "Real payments, reversals and async listeners over a shared Postgres; each scenario needs its own data and the class shares one Spring context, so it cannot be split without a new context")
class LateFeeSettlementIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private MeterRegistry meterRegistry;

    private void assertContiguousCoverage(Invoice origin, String firstDay, String lastDay) {
        List<Map<String, Object>> charges = chargesOf(origin);
        assertThat(charges.get(0).get("period_start").toString()).isEqualTo(firstDay);
        assertThat(charges.get(charges.size() - 1).get("period_end").toString()).isEqualTo(lastDay);
        for (int i = 1; i < charges.size(); i++) {
            assertThat(java.time.LocalDate.parse(charges.get(i).get("period_start").toString()))
                    .isEqualTo(java.time.LocalDate.parse(charges.get(i - 1).get("period_end").toString()).plusDays(1));
        }
    }

    @Test
    @Tag("D-331")
    void paymentOnTheDueDateIssuesNothingAndTheCloseOfTheMonthDoesNotEither() {
        arrange(2201);
        publish(0, "0.002", 10, true, true, d(2201, 1, 1));
        Invoice origin = pendingInstallmentDueOn(d(2201, 3, 11));

        payInFull(origin, d(2201, 3, 11), d(2201, 3, 11));
        awaitEventsDrained();
        reconcile(origin, d(2201, 4, 1));

        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(chargesOf(origin)).isEmpty();
        assertScenarioInvariants(d(2201, 4, 1));
    }

    @Test
    @Tag("D-331")
    void paymentInsideTheGraceIncludingTheLastGraceDayIssuesNothing() {
        arrange(2202);
        publish(10, "0.002", 10, true, true, d(2202, 1, 1));
        Invoice first = pendingInstallmentDueOn(d(2202, 3, 11));
        Invoice second = pendingInstallmentDueOn(d(2202, 4, 11));

        payInFull(first, d(2202, 3, 20), d(2202, 3, 20));
        awaitEventsDrained();
        payInFull(second, d(2202, 4, 21), d(2202, 4, 21));
        awaitEventsDrained();
        reconcile(first, d(2202, 5, 1));
        reconcile(second, d(2202, 5, 1));

        assertThat(chargesOf(first)).isEmpty();
        assertThat(chargesOf(second)).isEmpty();
        assertScenarioInvariants(d(2202, 5, 1));
    }

    @Test
    @Tag("D-330")
    @Tag("D-332")
    void paymentInTheSameMonthIssuesTheFinalDebitNoteUpToThePaymentDate() {
        arrange(2203);
        publishDefaults(2203);
        Invoice origin = overdueInstallmentDueOn(d(2203, 3, 11));

        payInFull(origin, d(2203, 3, 20), d(2203, 3, 20));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2203-03-12..2203-03-20:9:1800.00");
        Map<String, Object> note = chargesOf(origin).get(0);
        assertThat(note.get("fact_date").toString()).isEqualTo("2203-03-20");
        assertThat(note.get("cutoff_kind")).isEqualTo("SETTLEMENT");
        assertThat(note.get("settlement_allocation_id")).isNotNull();
        assertThat(note.get("nd_issue").toString()).isEqualTo("2203-03-20");
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertScenarioInvariants(d(2203, 3, 20));
    }

    @Test
    @Tag("D-330")
    @Tag("D-332")
    void paymentOnTheTwentyFifthChargesFourteenDays() {
        arrange(2204);
        publishDefaults(2204);
        Invoice origin = overdueInstallmentDueOn(d(2204, 3, 11));

        payInFull(origin, d(2204, 3, 25), d(2204, 3, 25));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2204-03-12..2204-03-25:14:2800.00");
        assertScenarioInvariants(d(2204, 3, 25));
    }

    @Test
    @Tag("D-331")
    @Tag("D-332")
    void withGraceThePaymentAfterTheGraceChargesFromTheOriginalDueDate() {
        arrange(2205);
        publish(10, "0.002", 10, true, true, d(2205, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2205, 3, 11));

        payInFull(origin, d(2205, 3, 25), d(2205, 3, 25));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2205-03-12..2205-03-25:14:2800.00");
        assertScenarioInvariants(d(2205, 3, 25));
    }

    @Test
    @Tag("D-243")
    void theMoneyDateAndNotTheAllocationDateCutsTheDays() {
        arrange(2206);
        publishDefaults(2206);
        Invoice origin = overdueInstallmentDueOn(d(2206, 3, 11));

        payInFull(origin, d(2206, 3, 20), d(2206, 3, 23));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2206-03-12..2206-03-20:9:1800.00");
        assertThat(chargesOf(origin).get(0).get("nd_issue").toString()).isEqualTo("2206-03-23");
        assertThat(chargesOf(origin).get(0).get("fact_date").toString()).isEqualTo("2206-03-20");
        assertScenarioInvariants(d(2206, 3, 23));
    }

    @Test
    @Tag("D-332")
    void cashLoadedOnTheLastDayOfTheMonthIssuesASingleDebitNoteAndTheCloseAddsNothing() {
        arrange(2207);
        publishDefaults(2207);
        Invoice origin = overdueInstallmentDueOn(d(2207, 3, 11));

        payInFull(origin, d(2207, 3, 31), d(2207, 3, 31));
        awaitEventsDrained();
        reconcile(origin, d(2207, 4, 1));

        assertThat(summaryOf(origin)).containsExactly("2207-03-12..2207-03-31:20:4000.00");
        assertThat(chargesOf(origin).get(0).get("cutoff_kind")).isEqualTo("SETTLEMENT");
        assertScenarioInvariants(d(2207, 4, 1));
    }

    @Test
    @Tag("D-332")
    void theCloseOfTheMonthBeforeThePaymentDatedTheLastDayLeavesTheSingleDebitNoteOfTheClose() {
        arrange(2208);
        publishDefaults(2208);
        Invoice origin = overdueInstallmentDueOn(d(2208, 3, 11));

        reconcile(origin, d(2208, 4, 1));
        payInFull(origin, d(2208, 3, 31), d(2208, 4, 1));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2208-03-12..2208-03-31:20:4000.00");
        assertThat(chargesOf(origin).get(0).get("cutoff_kind")).isEqualTo("MONTH_CLOSE");
        assertScenarioInvariants(d(2208, 4, 1));
    }

    @Test
    @Tag("D-332")
    void paymentInALaterMonthIssuesTheFinalDebitNoteForThatMonthOnly() {
        arrange(2209);
        publishDefaults(2209);
        Invoice origin = overdueInstallmentDueOn(d(2209, 3, 11));
        reconcile(origin, d(2209, 4, 1));

        payInFull(origin, d(2209, 4, 9), d(2209, 4, 9));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2209-03-12..2209-03-31:20:4000.00", "2209-04-01..2209-04-09:9:1800.00");
        assertThat(chargesOf(origin).get(1).get("cutoff_kind")).isEqualTo("SETTLEMENT");
        assertScenarioInvariants(d(2209, 4, 9));
    }

    @Test
    @Tag("D-332")
    void paymentOnTheFirstDayAfterTheCloseChargesThatSingleDay() {
        arrange(2210);
        publishDefaults(2210);
        Invoice origin = overdueInstallmentDueOn(d(2210, 3, 11));
        reconcile(origin, d(2210, 4, 1));

        payInFull(origin, d(2210, 4, 1), d(2210, 4, 1));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2210-03-12..2210-03-31:20:4000.00", "2210-04-01..2210-04-01:1:200.00");
        assertScenarioInvariants(d(2210, 4, 1));
    }

    @Test
    @Tag("D-332")
    @Tag("D-325")
    void paymentBeforeAMissedCloseCoversTheUnclosedMonthsToo() {
        arrange(2211);
        publishDefaults(2211);
        Invoice origin = overdueInstallmentDueOn(d(2211, 3, 11));

        payInFull(origin, d(2211, 4, 3), d(2211, 4, 3));
        awaitEventsDrained();
        reconcile(origin, d(2211, 4, 6));

        assertThat(summaryOf(origin)).containsExactly("2211-03-12..2211-03-31:20:4000.00", "2211-04-01..2211-04-03:3:600.00");
        assertThat(chargesOf(origin).get(0).get("fact_date").toString()).isEqualTo("2211-03-31");
        assertThat(chargesOf(origin).get(0).get("cutoff_kind")).isEqualTo("SETTLEMENT");
        assertScenarioInvariants(d(2211, 4, 6));
    }

    @Test
    @Tag("D-243")
    @Tag("D-332")
    void paymentDatedBeforeAnIssuedCloseIssuesNothingAndRecordsTheExcess() {
        arrange(2212);
        publishDefaults(2212);
        Invoice origin = overdueInstallmentDueOn(d(2212, 3, 11));
        reconcile(origin, d(2212, 4, 2));
        double before = meterRegistry.counter("billing_late_fees_overcharged_days_total").count();

        payInFull(origin, d(2212, 3, 28), d(2212, 4, 2));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2212-03-12..2212-03-31:20:4000.00");
        assertThat(meterRegistry.counter("billing_late_fees_overcharged_days_total").count() - before).isEqualTo(3.0);
        assertScenarioInvariants(d(2212, 4, 2));
    }

    @Test
    @Tag("D-332")
    void paymentDatedBeforeAMissedCloseIssuesASingleDebitNoteUpToTheMoneyDate() {
        arrange(2213);
        publishDefaults(2213);
        Invoice origin = overdueInstallmentDueOn(d(2213, 3, 11));
        double before = meterRegistry.counter("billing_late_fees_overcharged_days_total").count();

        payInFull(origin, d(2213, 3, 28), d(2213, 4, 2));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2213-03-12..2213-03-28:17:3400.00");
        assertThat(meterRegistry.counter("billing_late_fees_overcharged_days_total").count() - before).isZero();
        assertScenarioInvariants(d(2213, 4, 2));
    }

    @Test
    @Tag("D-140")
    @Tag("D-139")
    void partialMoneyDoesNotStopTheDaysNorIssuesTheFinalDebitNote() {
        arrange(2214);
        publishDefaults(2214);
        Invoice origin = overdueInstallmentDueOn(d(2214, 3, 11));

        receiveMoney(origin, "50000.00", d(2214, 3, 20));
        allocateOn(origin, d(2214, 3, 20));
        awaitEventsDrained();
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(chargesOf(origin)).isEmpty();
        reconcile(origin, d(2214, 4, 1));
        receiveMoney(origin, "50000.00", d(2214, 4, 9));
        allocateOn(origin, d(2214, 4, 9));
        awaitEventsDrained();

        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(summaryOf(origin)).containsExactly("2214-03-12..2214-03-31:20:4000.00", "2214-04-01..2214-04-09:9:1800.00");
        assertScenarioInvariants(d(2214, 4, 9));
    }

    @Test
    @Tag("D-332")
    @Tag("D-290")
    void reversalResumesFromTheDayAfterTheLastCoveredDay() {
        arrange(2215);
        publishDefaults(2215);
        Invoice origin = overdueInstallmentDueOn(d(2215, 3, 11));
        reconcile(origin, d(2215, 4, 1));
        CurrentAccountEntry credit = payInFull(origin, d(2215, 4, 9), d(2215, 4, 9));
        awaitEventsDrained();

        reversePayment(origin, credit, d(2215, 4, 15), 0);
        awaitEventsDrained();
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        reconcile(origin, d(2215, 5, 1));
        payInFull(origin, d(2215, 5, 10), d(2215, 5, 10));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2215-03-12..2215-03-31:20:4000.00", "2215-04-01..2215-04-09:9:1800.00", "2215-04-10..2215-04-30:21:4200.00",
                "2215-05-01..2215-05-10:10:2000.00");
        assertContiguousCoverage(origin, "2215-03-12", "2215-05-10");
        assertScenarioInvariants(d(2215, 5, 10));
    }

    @Test
    @Tag("D-331")
    void reversalOfAPaymentMadeBeforeTheDueDateChargesFromTheDueDate() {
        arrange(2216);
        publishDefaults(2216);
        Invoice origin = pendingInstallmentDueOn(d(2216, 3, 11));
        CurrentAccountEntry credit = payInFull(origin, d(2216, 3, 5), d(2216, 3, 5));
        awaitEventsDrained();
        assertThat(chargesOf(origin)).isEmpty();

        reversePayment(origin, credit, d(2216, 4, 20), 0);
        awaitEventsDrained();
        assertThat(summaryOf(origin)).containsExactly("2216-03-12..2216-03-31:20:4000.00");
        reconcile(origin, d(2216, 5, 1));

        assertThat(summaryOf(origin)).containsExactly("2216-03-12..2216-03-31:20:4000.00", "2216-04-01..2216-04-30:30:6000.00");
        assertScenarioInvariants(d(2216, 5, 1));
    }

    @Test
    @Tag("D-293")
    void withdrawalBeforePaymentCutsTheFinalDebitNoteAtTheEndDate() {
        arrange(2217);
        publishDefaults(2217);
        Invoice origin = overdueInstallmentDueOn(d(2217, 3, 11));
        reconcile(origin, d(2217, 4, 1));
        withdrawCourseEnrollmentOn(d(2217, 4, 20));

        payInFull(origin, d(2217, 4, 30), d(2217, 4, 30));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2217-03-12..2217-03-31:20:4000.00", "2217-04-01..2217-04-20:20:4000.00");
        assertScenarioInvariants(d(2217, 4, 30));
    }

    @Test
    @Tag("D-288")
    void versionChangeBetweenCloseAndPaymentKeepsTheDueDateRate() {
        arrange(2218);
        publish(0, "0.002", 10, true, true, d(2218, 1, 1));
        publish(0, "0.003", 10, true, true, d(2218, 4, 1));
        Invoice origin = overdueInstallmentDueOn(d(2218, 3, 11));
        reconcile(origin, d(2218, 4, 1));

        payInFull(origin, d(2218, 4, 9), d(2218, 4, 9));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2218-03-12..2218-03-31:20:4000.00", "2218-04-01..2218-04-09:9:1800.00");
        assertThat((BigDecimal) chargesOf(origin).get(1).get("daily_rate")).isEqualByComparingTo("0.002");
        assertScenarioInvariants(d(2218, 4, 9));
    }

    @Test
    @Tag("D-210")
    @Tag("D-294")
    void settlementCountsOnlyTheDaysWithChargingEnabled() {
        arrange(2219);
        publish(0, "0.002", 10, true, false, d(2219, 1, 1));
        publish(0, "0.002", 10, true, true, d(2219, 3, 16));
        Invoice origin = overdueInstallmentDueOn(d(2219, 3, 11));

        payInFull(origin, d(2219, 3, 20), d(2219, 3, 20));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2219-03-12..2219-03-20:5:1000.00");
        assertScenarioInvariants(d(2219, 3, 20));
    }
}
