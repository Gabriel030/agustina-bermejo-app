package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.service.CancelInvoiceOperation;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.Currency;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.red_escuela.backend.shared.domain.Percentage;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import static com.red_escuela.backend.billing.invoice.operation.IssuingTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("D-074")
@Tag("D-283")
class CohortPromotionLifecycleIT extends AbstractInvoiceIssuingIT {

    @Autowired
    private BillingOperationPipeline pipeline;
    @Autowired
    private CancelInvoiceOperation cancelInvoiceOperation;

    private Percentage percent(String points) {
        return Percentage.ofPoints(new BigDecimal(points));
    }

    private Invoice issueInstallmentOn(IssuingScenarioFactory.Offering offering, IssuingScenarioFactory.Learner learner, int month, LocalDate today) {
        today(today);
        return issued(runner.issueInvoiceInNewTransaction(installment(offering, learner, YearMonth.of(2027, month), today.plusDays(10), LocalDate.of(2027, month, 1))));
    }

    private void cancel(IssuingScenarioFactory.Offering offering, Invoice invoice) {
        CancelInvoiceCommand command = CancelInvoiceCommand.byPerson(invoice.getId(), CreditNoteReason.BILLING_ERROR, "Error de facturación", offering.directorId(), true);
        pipeline.runOperation(cancelInvoiceOperation, command).resultOrThrowIfAwaitingAuthorization(id -> new IllegalStateException("Unexpected authorization " + id));
    }

    @Test
    void aCancelledLastInstallmentLeavesThePromotionExhaustedAndTheReissueKeepsTheSameDiscountNumber() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 3);

        issueInstallmentOn(offering, learner, 3, LocalDate.of(2027, 3, 1));
        issueInstallmentOn(offering, learner, 4, LocalDate.of(2027, 4, 1));
        Invoice may = issueInstallmentOn(offering, learner, 5, LocalDate.of(2027, 5, 1));
        assertThat(may.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("3 de 3");
        assertThat(jdbc.queryForObject("select status from student_discount where id = ?", String.class, promotionId)).isEqualTo("EXHAUSTED");

        cancel(offering, may);
        Invoice reissued = issueInstallmentOn(offering, learner, 5, LocalDate.of(2027, 5, 2));

        assertThat(reissued.getLines().get(0).getDiscount().getStudentDiscountId()).isEqualTo(promotionId);
        assertThat(reissued.getLines().get(0).getDiscount().describeInstallmentProgress()).contains("3 de 3");
        assertThat(reissued.getAmounts().total()).isEqualTo(ars("56000.00"));
        invariants.assertAllBillingInvariantsHold();
    }

    @Test
    void aReleasedLastInstallmentIsNotRestoredOnceTheAvailableUntilDateHasPassed() {
        var offering = factory.newProgram(Currency.ARS, 80000, 50000);
        var learner = factory.newLearner(offering, null);
        Long promotionId = factory.acquireCohortPromotion(offering, learner, FeeType.MONTHLY_INSTALLMENT, percent("30"), 3, LocalDate.of(2027, 5, 31));

        issueInstallmentOn(offering, learner, 3, LocalDate.of(2027, 3, 1));
        issueInstallmentOn(offering, learner, 4, LocalDate.of(2027, 4, 1));
        Invoice may = issueInstallmentOn(offering, learner, 5, LocalDate.of(2027, 5, 1));
        assertThat(jdbc.queryForObject("select status from student_discount where id = ?", String.class, promotionId)).isEqualTo("EXHAUSTED");

        cancel(offering, may);
        Invoice reissued = issueInstallmentOn(offering, learner, 5, LocalDate.of(2027, 6, 2));

        assertThat(reissued.getLines().get(0).getDiscount()).isNull();
        assertThat(reissued.getAmounts().total()).isEqualTo(ars("80000.00"));
    }
}
