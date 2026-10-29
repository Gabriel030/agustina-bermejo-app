package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.collections.operation.LateFeeCorrectionSweepResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeWithdrawalCorrectionStep;
import com.red_escuela.backend.billing.collections.operation.OverdueDetectionResult;
import com.red_escuela.backend.billing.collections.operation.OverdueInvoiceDetectionStep;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-451")
class BillingRunStepAdaptersTest {
    private static final LocalDate DAY = LocalDate.of(2031, 10, 7);

    @Test
    void theOverdueStepSeparatesSkippedFromFailedInvoicesAndCountsTheRestAsProcessed() {
        OverdueInvoiceDetectionStep legacy = mock(OverdueInvoiceDetectionStep.class);
        when(legacy.detectOverdueInvoices(DAY)).thenReturn(new OverdueDetectionResult(10, 6, 1, 1, List.of(
                Map.of("invoiceId", 5L, "message", "Sin versión de mora", "decisionCode", OverdueInvoiceDetectionStep.SKIPPED_WITHOUT_LATE_PAYMENT_TERMS_CODE),
                Map.of("invoiceId", 6L, "message", "boom"))));

        BillingRunStepOutcome outcome = new OverdueDetectionBillingRunStep(legacy).runStepForBusinessDate(DAY);

        assertThat(outcome.candidates()).isEqualTo(10);
        assertThat(outcome.processed()).isEqualTo(8);
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.items()).satisfiesExactly(
                skipped -> {
                    assertThat(skipped.subjectType()).isEqualTo(BillingRunSubjectType.INVOICE);
                    assertThat(skipped.subjectId()).isEqualTo(5L);
                    assertThat(skipped.status()).isEqualTo(BillingRunItemStatus.SKIPPED);
                    assertThat(skipped.decisionCode()).isEqualTo("NO_LATE_PAYMENT_TERMS");
                },
                failed -> {
                    assertThat(failed.subjectId()).isEqualTo(6L);
                    assertThat(failed.status()).isEqualTo(BillingRunItemStatus.FAILED);
                    assertThat(failed.errorCode()).isEqualTo(AbstractBillingRunStep.SUBJECT_PROCESSING_FAILED);
                    assertThat(failed.message()).isEqualTo("boom");
                });
    }

    @Test
    void theLateFeeStepCountsBothTheOverdueAndTheRecentlySettledCandidates() {
        LateFeeAccrualStep legacy = mock(LateFeeAccrualStep.class);
        when(legacy.accrueLateFees(DAY)).thenReturn(new LateFeeAccrualResult(7, 4, 1, List.of(Map.of("invoiceId", 3L, "message", "x")), 1, 5));

        BillingRunStepOutcome outcome = new LateFeeAccrualBillingRunStep(legacy).runStepForBusinessDate(DAY);

        assertThat(outcome.candidates()).isEqualTo(12);
        assertThat(outcome.processed()).isEqualTo(11);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.items()).singleElement().satisfies(item -> assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.INVOICE));
    }

    @Test
    void theCorrectionStepReportsItsFailuresAgainstTheLateFeeCharge() {
        LateFeeWithdrawalCorrectionStep legacy = mock(LateFeeWithdrawalCorrectionStep.class);
        when(legacy.correctLateFeesChargedAfterTheEnrollmentEnd(DAY)).thenReturn(new LateFeeCorrectionSweepResult(4, 1, 2, 1, List.of(Map.of("lateFeeChargeId", 40L, "message", "y"))));

        BillingRunStepOutcome outcome = new LateFeeWithdrawalCorrectionBillingRunStep(legacy).runStepForBusinessDate(DAY);

        assertThat(outcome.processed()).isEqualTo(3);
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.LATE_FEE_CHARGE);
            assertThat(item.subjectId()).isEqualTo(40L);
        });
    }

    @Test
    @Tag("D-447")
    void theGatewayStepDeclaresItselfNotApplicableWhileThePaymentGatewayIsOff() {
        assertThat(new GatewaySyncBillingRunStep().findNotApplicableCode(DAY)).contains("GATEWAY_DISABLED");
    }
}
