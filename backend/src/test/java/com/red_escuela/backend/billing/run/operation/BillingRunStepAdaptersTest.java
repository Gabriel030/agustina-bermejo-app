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
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontExpiryResult;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-451")
class BillingRunStepAdaptersTest {
    private static final LocalDate DAY = LocalDate.of(2031, 10, 7);
    private final HeldAccountLookup heldAccounts = mock(HeldAccountLookup.class);

    @Test
    void theOverdueStepSeparatesSkippedFromFailedInvoicesAndCountsTheRestAsProcessed() {
        OverdueInvoiceDetectionStep legacy = mock(OverdueInvoiceDetectionStep.class);
        when(legacy.detectOverdueInvoices(eq(DAY), any())).thenReturn(new OverdueDetectionResult(10, 6, 1, 1, List.of(
                Map.of("invoiceId", 5L, "message", "Sin versión de mora", "decisionCode", OverdueInvoiceDetectionStep.SKIPPED_WITHOUT_LATE_PAYMENT_TERMS_CODE),
                Map.of("invoiceId", 6L, "message", "boom"))));

        BillingRunStepOutcome outcome = new OverdueDetectionBillingRunStep(legacy, heldAccounts).runStepForBusinessDate(DAY);

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
        when(legacy.accrueLateFees(eq(DAY), any())).thenReturn(new LateFeeAccrualResult(7, 4, 1, List.of(Map.of("invoiceId", 3L, "message", "x")), 1, 5));

        BillingRunStepOutcome outcome = new LateFeeAccrualBillingRunStep(legacy, heldAccounts).runStepForBusinessDate(DAY);

        assertThat(outcome.candidates()).isEqualTo(12);
        assertThat(outcome.processed()).isEqualTo(11);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.items()).singleElement().satisfies(item -> assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.INVOICE));
    }

    @Test
    void theCorrectionStepReportsItsFailuresAgainstTheLateFeeCharge() {
        LateFeeWithdrawalCorrectionStep legacy = mock(LateFeeWithdrawalCorrectionStep.class);
        when(legacy.correctLateFeesChargedAfterTheEnrollmentEnd(eq(DAY), any())).thenReturn(new LateFeeCorrectionSweepResult(4, 1, 2, 1, List.of(Map.of("lateFeeChargeId", 40L, "message", "y"))));

        BillingRunStepOutcome outcome = new LateFeeWithdrawalCorrectionBillingRunStep(legacy, heldAccounts).runStepForBusinessDate(DAY);

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

    @Test
    @Tag("D-155")
    void theAnnualUpfrontExpiryStepReportsEachFailedRequestAndCountsExpiredAndSkippedSeparately() {
        AnnualUpfrontDeadlineExpirer expirer = mock(AnnualUpfrontDeadlineExpirer.class);
        when(expirer.expireRequestsPastDeadline()).thenReturn(new AnnualUpfrontExpiryResult(3, 2, List.of(41L, 42L)));
        AnnualUpfrontExpiryBillingRunStep step = new AnnualUpfrontExpiryBillingRunStep(expirer);

        BillingRunStepOutcome outcome = step.runStepForBusinessDate(DAY);

        assertThat(step.step()).isEqualTo(BillingRunStep.ANNUAL_UPFRONT_EXPIRY);
        assertThat(outcome.candidates()).isEqualTo(7);
        assertThat(outcome.processed()).isEqualTo(3);
        assertThat(outcome.skipped()).isEqualTo(2);
        assertThat(outcome.failed()).isEqualTo(2);
        assertThat(outcome.hasErrors()).isTrue();
        assertThat(outcome.items()).extracting(item -> item.subjectType(), item -> item.subjectId(), item -> item.status(), item -> item.errorCode())
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(BillingRunSubjectType.ANNUAL_UPFRONT_REQUEST, 41L, BillingRunItemStatus.FAILED, AnnualUpfrontExpiryBillingRunStep.ANNUAL_UPFRONT_EXPIRY_FAILED),
                        org.assertj.core.api.Assertions.tuple(BillingRunSubjectType.ANNUAL_UPFRONT_REQUEST, 42L, BillingRunItemStatus.FAILED, AnnualUpfrontExpiryBillingRunStep.ANNUAL_UPFRONT_EXPIRY_FAILED));
    }

    @Test
    @Tag("D-155")
    void theAnnualUpfrontExpiryStepWithNothingToExpireReportsNoItemsAndNoErrors() {
        AnnualUpfrontDeadlineExpirer expirer = mock(AnnualUpfrontDeadlineExpirer.class);
        when(expirer.expireRequestsPastDeadline()).thenReturn(new AnnualUpfrontExpiryResult(0, 0, List.of()));

        BillingRunStepOutcome outcome = new AnnualUpfrontExpiryBillingRunStep(expirer).runStepForBusinessDate(DAY);

        assertThat(outcome.candidates()).isZero();
        assertThat(outcome.items()).isEmpty();
        assertThat(outcome.hasErrors()).isFalse();
    }
}
