package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.note.domain.CreditNoteDestination;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineAmount;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.service.IssueCreditNoteOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.time.MutableClock;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-377")
@Tag("D-297")
class LateFeeDebtReductionSettlementIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation issueCreditNoteOperation;
    @Autowired
    private LateFeeAccrualStep accrualStep;
    @Autowired
    private LateFeeChargeRepository chargeRepository;
    @Autowired
    private MeterRegistry meterRegistry;

    private void reduceDebtOn(Invoice origin, String amount, LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
        pipeline.runOperation(issueCreditNoteOperation, new IssueCreditNoteCommand(origin.getId(), CreditNoteReason.BILLING_ERROR, "Error de carga",
                List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal(amount), Currency.ARS))), CreditNoteDestination.DEBT_OFFSET, person.getId(), true, null));
        awaitEventsDrained();
    }

    private double overchargedDays() {
        return meterRegistry.counter("billing_late_fees_overcharged_days_total").count();
    }

    @Test
    @Tag("9B2-47")
    void debtReductionCreditNoteThatSettlesTheInstallmentCutsTheFinalDebitNoteAtItsDate() {
        arrange(2301);
        publishDefaults(2301);
        Invoice origin = overdueInstallmentDueOn(d(2301, 3, 11));

        settleByDebtReductionCreditNote(origin, d(2301, 3, 20));

        assertThat(summaryOf(origin)).containsExactly("2301-03-12..2301-03-20:9:1800.00");
        Map<String, Object> note = chargesOf(origin).get(0);
        assertThat(note.get("cutoff_kind")).isEqualTo("SETTLEMENT");
        assertThat(note.get("settlement_allocation_id")).isNull();
        assertThat(note.get("settlement_credit_note_id")).isEqualTo(jdbc.queryForObject("select id from credit_note where invoice_id = ?", Long.class, origin.getId()));
        assertThat(note.get("fact_date").toString()).isEqualTo("2301-03-20");
        assertThat(note.get("nd_issue").toString()).isEqualTo("2301-03-20");
        assertThat((BigDecimal) note.get("base_amount")).isEqualByComparingTo("100000.00");
        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertScenarioInvariants(d(2301, 3, 20));
    }

    @Test
    @Tag("9B2-47")
    void aPartialCreditNoteIssuesNoFinalDebitNoteAndTheMonthCloseKeepsTheFinalPriceAsBase() {
        arrange(2302);
        publishDefaults(2302);
        Invoice origin = overdueInstallmentDueOn(d(2302, 3, 11));

        reduceDebtOn(origin, "40000.00", d(2302, 3, 15));

        assertThat(reload(origin).getPaymentStatus()).isEqualTo(InvoicePaymentStatus.OVERDUE);
        assertThat(chargesOf(origin)).isEmpty();
        reconcile(origin, d(2302, 4, 1));
        assertThat(summaryOf(origin)).containsExactly("2302-03-12..2302-03-31:20:4000.00");
        assertThat((BigDecimal) chargesOf(origin).get(0).get("base_amount")).isEqualByComparingTo("100000.00");
        assertThat(chargesOf(origin).get(0).get("cutoff_kind")).isEqualTo("MONTH_CLOSE");
        assertScenarioInvariants(d(2302, 4, 1));
    }

    @Test
    @Tag("9B2-47")
    void aCreditNoteThatSettlesTheRemainderAfterAPartialOneCutsAtTheLastCreditNote() {
        arrange(2303);
        publishDefaults(2303);
        Invoice origin = overdueInstallmentDueOn(d(2303, 3, 11));

        reduceDebtOn(origin, "40000.00", d(2303, 3, 15));
        settleByDebtReductionCreditNote(origin, d(2303, 3, 22));

        assertThat(summaryOf(origin)).containsExactly("2303-03-12..2303-03-22:11:2200.00");
        Long lastNote = jdbc.queryForObject("select max(id) from credit_note where invoice_id = ?", Long.class, origin.getId());
        assertThat(chargesOf(origin).get(0).get("settlement_credit_note_id")).isEqualTo(lastNote);
        assertScenarioInvariants(d(2303, 3, 22));
    }

    @Test
    @Tag("9B2-47")
    void aPaymentAfterAPartialCreditNoteStillCutsAtTheMoneyDateWithTheAllocationAsSource() {
        arrange(2304);
        publishDefaults(2304);
        Invoice origin = overdueInstallmentDueOn(d(2304, 3, 11));

        reduceDebtOn(origin, "40000.00", d(2304, 3, 15));
        receiveMoney(origin, "60000.00", d(2304, 3, 20));
        allocateOn(origin, d(2304, 3, 20));
        awaitEventsDrained();

        assertThat(summaryOf(origin)).containsExactly("2304-03-12..2304-03-20:9:1800.00");
        assertThat(chargesOf(origin).get(0).get("settlement_allocation_id")).isNotNull();
        assertThat(chargesOf(origin).get(0).get("settlement_credit_note_id")).isNull();
        assertScenarioInvariants(d(2304, 3, 20));
    }

    @Test
    @Tag("D-332")
    void theMonthCloseAfterTheSettlingCreditNoteAndAnEventRedeliveryIssueNothingMore() {
        arrange(2305);
        publishDefaults(2305);
        Invoice origin = overdueInstallmentDueOn(d(2305, 3, 11));
        settleByDebtReductionCreditNote(origin, d(2305, 3, 20));

        int redelivered = accrualService.reconcileLateFeesOfSettledInvoice(origin.getId(), d(2305, 3, 20));
        reconcile(origin, d(2305, 4, 1));

        assertThat(redelivered).isZero();
        assertThat(summaryOf(origin)).containsExactly("2305-03-12..2305-03-20:9:1800.00");
        assertScenarioInvariants(d(2305, 4, 1));
    }

    @Test
    @Tag("D-332")
    void aCreditNoteAfterTheClosedMonthCutsTheNextMonthAtItsDate() {
        arrange(2306);
        publishDefaults(2306);
        Invoice origin = overdueInstallmentDueOn(d(2306, 3, 11));
        reconcile(origin, d(2306, 4, 1));

        settleByDebtReductionCreditNote(origin, d(2306, 4, 15));

        assertThat(summaryOf(origin)).containsExactly("2306-03-12..2306-03-31:20:4000.00", "2306-04-01..2306-04-15:15:3000.00");
        assertScenarioInvariants(d(2306, 4, 15));
    }

    @Test
    @Tag("D-380")
    void aCreditNoteDatedBeforeAnIssuedCloseRecordsTheChargedDaysAfterItOnceAndIssuesNothing() {
        arrange(2307);
        publishDefaults(2307);
        Invoice origin = overdueInstallmentDueOn(d(2307, 3, 11));
        reconcile(origin, d(2307, 5, 1));
        double before = overchargedDays();

        settleByDebtReductionCreditNote(origin, d(2307, 4, 20));
        accrualService.reconcileLateFeesOfSettledInvoice(origin.getId(), d(2307, 4, 20));

        assertThat(summaryOf(origin)).containsExactly("2307-03-12..2307-03-31:20:4000.00", "2307-04-01..2307-04-30:30:6000.00");
        assertThat(overchargedDays() - before).isEqualTo(10.0);
    }

    @Test
    @Tag("D-332")
    void aSettlementByCreditNoteWhoseListenerFoundNothingToChargeIsRecoveredOnceByTheNightlyRun() {
        arrange(2308);
        publish(0, "0.002", 10, true, false, d(2308, 1, 1));
        Invoice origin = overdueInstallmentDueOn(d(2308, 3, 11));
        settleByDebtReductionCreditNote(origin, d(2308, 3, 20));
        assertThat(summaryOf(origin)).isEmpty();
        publish(0, "0.002", 10, true, true, d(2308, 1, 2));
        assertThat(invariants.lateFeesCoverEveryChargeableDayAt(d(2308, 3, 25))).hasSize(9);

        LateFeeAccrualResult first = accrualStep.accrueLateFees(d(2308, 3, 25));
        LateFeeAccrualResult second = accrualStep.accrueLateFees(d(2308, 3, 26));

        assertThat(first.settlementDebitNotesRecovered()).isEqualTo(1);
        assertThat(second.settlementDebitNotesRecovered()).isZero();
        assertThat(summaryOf(origin)).containsExactly("2308-03-12..2308-03-20:9:1800.00");
        assertThat(chargesOf(origin).get(0).get("settlement_credit_note_id")).isNotNull();
        assertThat(chargeRepository.findRecentlySettledLateFeeOriginIdsAfter(0L, d(2308, 2, 1), 10)).doesNotContain(origin.getId());
        assertScenarioInvariants(d(2308, 3, 26));
    }

    @Test
    @Tag("9B2-47")
    void theDatabaseRequiresExactlyOneSettlementSourceOnASettlementChargeAndNoneOnAMonthClose() {
        arrange(2309);
        publishDefaults(2309);
        Invoice settled = overdueInstallmentDueOn(d(2309, 3, 11));
        settleByDebtReductionCreditNote(settled, d(2309, 3, 20));
        Invoice closed = overdueInstallmentDueOn(d(2309, 4, 11));
        reconcile(closed, d(2309, 5, 1));
        Long settledCharge = (Long) chargesOf(settled).get(0).get("id");
        Long closedCharge = (Long) chargesOf(closed).get(0).get("id");
        Long creditNoteId = jdbc.queryForObject("select id from credit_note where invoice_id = ?", Long.class, settled.getId());

        assertThatThrownBy(() -> jdbc.update("update late_fee_charge set settlement_credit_note_id = null where id = ?", settledCharge))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_late_fee_charge_settlement_source");
        assertThatThrownBy(() -> jdbc.update("update late_fee_charge set settlement_credit_note_id = ? where id = ?", creditNoteId, closedCharge))
                .isInstanceOf(DataIntegrityViolationException.class).hasMessageContaining("ck_late_fee_charge_settlement_source");
    }
}
