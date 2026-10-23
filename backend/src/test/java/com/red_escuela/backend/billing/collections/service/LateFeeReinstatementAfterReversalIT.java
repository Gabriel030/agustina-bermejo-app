package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.RegisterChargebackOperation;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackCommand;
import com.red_escuela.backend.billing.payment.operation.ReverseChargebackOperation;
import com.red_escuela.backend.billing.reporting.domain.CountValue;
import com.red_escuela.backend.billing.reporting.domain.MetricSeries;
import com.red_escuela.backend.billing.reporting.domain.MetricValue;
import com.red_escuela.backend.billing.reporting.domain.MetricFilter;
import com.red_escuela.backend.billing.reporting.domain.MonthRange;
import com.red_escuela.backend.billing.reporting.domain.MonthlyMetric;
import com.red_escuela.backend.billing.reporting.service.LateFeesMetricQuery;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.time.MutableClock;
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-414")
@SlowTest(reason = "Scenarios that seed overdue invoices, annul by payment date, charge a payment back and run the real late fee reconciliation and events against Postgres")
class LateFeeReinstatementAfterReversalIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private RegisterChargebackOperation chargebackOperation;
    @Autowired
    private ReverseChargebackOperation reverseChargebackOperation;
    @Autowired
    private LateFeesMetricQuery lateFeesMetricQuery;

    @AfterEach
    void resetClock() {
        MutableClock.INSTANCE.reset();
    }

    private record AnnulledMarch(Invoice origin, long paymentId) {
    }

    private AnnulledMarch marchAnnulledByAPaymentDatedThe28th(int year) {
        arrange(year);
        publishDefaults(year);
        Invoice origin = overdueInstallmentDueOn(d(year, 3, 11));
        reconcile(origin, d(year, 4, 1));
        MutableClock.INSTANCE.setDate(d(year, 4, 2));
        long paymentId = BillingTestFixtures.seedApprovedPaymentRow(origin.getStudentCurrentAccountId(), NonNegativeMoney.of(ars("100000.00")));
        transactionTemplate.executeWithoutResult(status -> accountService.recordPaymentReceivedCredit(accountService.lockStudentCurrentAccount(origin.getStudentCurrentAccountId()),
                paymentId, NonNegativeMoney.of(ars("100000.00")), d(year, 3, 28), "Pago"));
        allocateOn(origin, d(year, 4, 2));
        awaitEventsDrained();
        return new AnnulledMarch(origin, paymentId);
    }

    private Long chargeback(long paymentId, LocalDate chargedBackOn) {
        return pipeline.runOperation(chargebackOperation, new RegisterChargebackCommand(paymentId, ars("100000.00"), chargedBackOn, "ref-" + paymentId, ACTOR))
                .resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("authorization " + id)).chargebackId();
    }

    private List<Map<String, Object>> reinstatementsOf(Invoice origin) {
        return jdbc.queryForList("select c.*, d.total as nd_total, d.document_status as nd_status, d.payment_status as nd_payment_status from late_fee_charge c "
                + "join invoice d on d.id = c.debit_note_id where c.origin_invoice_id = ? and c.reinstates_correction_id is not null order by c.id", origin.getId());
    }

    private BigDecimal netBilledOf(Invoice origin) {
        return jdbc.queryForObject("select coalesce(sum(d.total - d.credited_amount), 0) from late_fee_charge c join invoice d on d.id = c.debit_note_id "
                + "where c.origin_invoice_id = ?", BigDecimal.class, origin.getId());
    }

    @Test
    @Tag("D-206")
    @Tag("D-094")
    void aChargebackReinstatesTheDaysThePaymentHadAnnulledWithADebitNoteForThoseDays() {
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(2401);
        assertThat(netBilledOf(march.origin())).isEqualByComparingTo("3400.00");
        MutableClock.INSTANCE.setDate(d(2401, 4, 10));

        chargeback(march.paymentId(), d(2401, 4, 10));
        awaitEventsDrained();

        List<Map<String, Object>> reinstated = reinstatementsOf(march.origin());
        assertThat(reinstated).hasSize(1);
        Map<String, Object> reinstatement = reinstated.get(0);
        assertThat(reinstatement.get("period_start").toString()).isEqualTo("2401-03-29");
        assertThat(reinstatement.get("period_end").toString()).isEqualTo("2401-03-31");
        assertThat(reinstatement).containsEntry("charged_days", 3).containsEntry("cutoff_kind", "REINSTATEMENT").containsEntry("nd_status", "ISSUED");
        assertThat((BigDecimal) reinstatement.get("nd_total")).isEqualByComparingTo("600.00");
        assertThat(reinstatement.get("reinstates_correction_id")).isEqualTo(
                jdbc.queryForObject("select id from late_fee_charge_correction where first_corrected_day = date '2401-03-29'", Long.class));
        assertThat(netBilledOf(march.origin())).isEqualByComparingTo("4000.00");
        assertThat(jdbc.queryForObject("select payment_status from invoice where id = ?", String.class, march.origin().getId())).isEqualTo("OVERDUE");
        assertScenarioInvariants(d(2401, 4, 10));
    }

    @Test
    @Tag("D-206")
    void reversingTheChargebackAndChargingTheSamePaymentBackAgainDoesNotReinstateTheSameDaysTwice() {
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(2402);
        MutableClock.INSTANCE.setDate(d(2402, 4, 10));
        Long chargebackId = chargeback(march.paymentId(), d(2402, 4, 10));
        awaitEventsDrained();
        assertThat(reinstatementsOf(march.origin())).hasSize(1);

        pipeline.runOperation(reverseChargebackOperation, new ReverseChargebackCommand(chargebackId, ACTOR));
        awaitEventsDrained();
        chargeback(march.paymentId(), d(2402, 4, 10));
        awaitEventsDrained();

        assertThat(reinstatementsOf(march.origin())).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from invoice where invoice_type = 'LATE_FEE' and student_current_account_id = ? "
                + "and total = 600.00", Long.class, march.origin().getStudentCurrentAccountId())).isEqualTo(1L);
        assertScenarioInvariants(d(2402, 4, 10));
    }

    @Test
    @Tag("D-414")
    void aRetroactiveChargebackStillChargesTheIntermediateDaysFromTheLastCoveredDayWithoutGapsOrOverlaps() {
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(2403);
        MutableClock.INSTANCE.setDate(d(2403, 4, 10));

        chargeback(march.paymentId(), d(2403, 4, 5));
        awaitEventsDrained();
        reconcile(march.origin(), d(2403, 5, 1));

        List<Map<String, Object>> charges = chargesOf(march.origin());
        assertThat(charges).extracting(c -> c.get("period_start") + ".." + c.get("period_end") + ":" + c.get("cutoff_kind"))
                .containsExactly("2403-03-12..2403-03-31:MONTH_CLOSE", "2403-03-29..2403-03-31:REINSTATEMENT", "2403-04-01..2403-04-30:MONTH_CLOSE");
        assertThat(netBilledOf(march.origin())).isEqualByComparingTo("10000.00");
        assertScenarioInvariants(d(2403, 5, 1));
    }

    @Test
    @Tag("D-415")
    void theLateFeePanelCountsTheReinstatementAsIssuedAndDoesNotChangeTheAnnulments() {
        AnnulledMarch march = marchAnnulledByAPaymentDatedThe28th(2404);
        MetricFilter filter = new MetricFilter(new MonthRange(YearMonth.of(2404, 4), YearMonth.of(2404, 4)), Currency.ARS, null, null, null);
        MonthlyMetric before = lateFeesMetricQuery.findSeries(filter);
        MutableClock.INSTANCE.setDate(d(2404, 4, 10));

        chargeback(march.paymentId(), d(2404, 4, 10));
        awaitEventsDrained();

        MonthlyMetric after = lateFeesMetricQuery.findSeries(filter);
        assertThat(valueOf(after, "annulledCount")).isEqualTo(valueOf(before, "annulledCount"));
        assertThat(valueOf(after, "annulledAmount")).isEqualTo(valueOf(before, "annulledAmount"));
        assertThat(((CountValue) valueOf(after, "issuedCount")).count()).isEqualTo(((CountValue) valueOf(before, "issuedCount")).count() + 1);
    }

    private static MetricValue valueOf(MonthlyMetric metric, String key) {
        MetricSeries series = metric.series().stream().filter(candidate -> candidate.key().equals(key)).findFirst().orElseThrow();
        return series.values().get(0);
    }
}
