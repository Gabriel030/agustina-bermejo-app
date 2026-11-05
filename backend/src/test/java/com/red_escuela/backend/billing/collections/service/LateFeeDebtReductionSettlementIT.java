package com.red_escuela.backend.billing.collections.service;

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
import com.red_escuela.backend.testsupport.SlowTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-377")
@Tag("D-297")
@SlowTest(reason = "Shares the heavy Spring context of the late fee scenario hierarchy: when it is the first class of its shard it absorbs the whole context start-up")
class LateFeeDebtReductionSettlementIT extends AbstractLateFeeScenarioIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private IssueCreditNoteOperation issueCreditNoteOperation;

    private void reduceDebtOn(Invoice origin, String amount, LocalDate date) {
        MutableClock.INSTANCE.setDate(date);
        pipeline.runOperation(issueCreditNoteOperation, new IssueCreditNoteCommand(origin.getId(), CreditNoteReason.BILLING_ERROR, "Error de carga",
                List.of(new CreditNoteLineAmount(1, Money.of(new BigDecimal(amount), Currency.ARS))), CreditNoteDestination.DEBT_OFFSET, person.getId(), true, null));
        awaitEventsDrained();
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
        assertScenarioInvariants(d(2304, 3, 20));
    }
}
