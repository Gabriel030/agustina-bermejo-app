package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.account.repository.StudentCurrentAccountRepository;
import com.red_escuela.backend.billing.account.service.AccountCreditAllocationOperation;
import com.red_escuela.backend.billing.collections.operation.CollectionsDailySnapshotStep;
import com.red_escuela.backend.billing.configuration.domain.AllocationSettings;
import com.red_escuela.backend.billing.configuration.service.BillingConfigurationReader;
import com.red_escuela.backend.billing.operation.BillingOperationOutcome;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.collections.domain.SuspendedEnrollmentClosureReport;
import com.red_escuela.backend.billing.service.SuspendedEnrollmentClosureService;
import com.red_escuela.backend.billing.collections.domain.EnrollmentComplianceSweepReport;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.operation.EnrollmentComplianceSweepPort;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-451")
class NightlyCompletionStepsTest {
    private static final LocalDate NIGHT = LocalDate.of(2031, 10, 7);

    private final EnrollmentComplianceSweepPort complianceSweep = mock(EnrollmentComplianceSweepPort.class);
    private final EnrollmentBillingComplianceBillingRunStep complianceStep = new EnrollmentBillingComplianceBillingRunStep(complianceSweep);
    private final BillingConfigurationReader configurationReader = mock(BillingConfigurationReader.class);

    @Test
    void aFailingEnrollmentBecomesAFailedItemOfItsKindAndTheRestCountsAsProcessed() {
        when(complianceSweep.sweepEnrollmentCompliance(NIGHT)).thenReturn(new EnrollmentComplianceSweepReport(3, List.of(
                new EnrollmentComplianceSweepReport.EnrollmentComplianceFailure(EnrollmentKind.PROGRAM, 7L, "boom"),
                new EnrollmentComplianceSweepReport.EnrollmentComplianceFailure(EnrollmentKind.COURSE, 8L, "bang"))));

        BillingRunStepOutcome outcome = complianceStep.runStepForBusinessDate(NIGHT);

        assertThat(outcome.candidates()).isEqualTo(3);
        assertThat(outcome.processed()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(2);
        assertThat(outcome.items()).extracting(item -> item.subjectType(), item -> item.subjectId(), item -> item.status()).containsExactly(
                org.assertj.core.groups.Tuple.tuple(BillingRunSubjectType.PROGRAM_ENROLLMENT, 7L, BillingRunItemStatus.FAILED),
                org.assertj.core.groups.Tuple.tuple(BillingRunSubjectType.COURSE_ENROLLMENT, 8L, BillingRunItemStatus.FAILED));
    }

    @Test
    void withoutALatePaymentPolicyTheComplianceIsNotApplicable() {
        when(complianceSweep.findNotApplicableCode(NIGHT)).thenReturn(Optional.of("NO_LATE_PAYMENT_POLICY"));
        when(complianceSweep.findNotApplicableCode(NIGHT.plusDays(1))).thenReturn(Optional.empty());

        assertThat(complianceStep.findNotApplicableCode(NIGHT)).contains("NO_LATE_PAYMENT_POLICY");
        assertThat(complianceStep.findNotApplicableCode(NIGHT.plusDays(1))).isEmpty();
    }

    @Test
    void theSnapshotStepTakesTheSnapshotOfTheBusinessDate() {
        CollectionsDailySnapshotStep snapshot = mock(CollectionsDailySnapshotStep.class);

        BillingRunStepOutcome outcome = new CollectionsSnapshotBillingRunStep(snapshot).runStepForBusinessDate(NIGHT);

        verify(snapshot).takeCollectionsSnapshot(NIGHT);
        assertThat(outcome.processed()).isEqualTo(1);
    }

    @Test
    void theTermEndStepSeparatesRuleRejectionsFromFailures() {
        SuspendedEnrollmentClosureService closure = mock(SuspendedEnrollmentClosureService.class);
        when(closure.closeFinishedCyclesReportingFailures()).thenReturn(new SuspendedEnrollmentClosureReport(5, 2, List.of(
                new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(11L, true, "WITHDRAWAL_REJECTED_BY_RULE", "RULE_X"),
                new SuspendedEnrollmentClosureReport.EnrollmentClosureFailure(12L, false, "SUBJECT_PROCESSING_FAILED", "boom"))));

        BillingRunStepOutcome outcome = new TermEndWithdrawalBillingRunStep(closure).runStepForBusinessDate(NIGHT);

        assertThat(outcome.candidates()).isEqualTo(5);
        assertThat(outcome.processed()).isEqualTo(3);
        assertThat(outcome.skipped()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.items()).extracting(item -> item.subjectId(), item -> item.status())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(11L, BillingRunItemStatus.SKIPPED), org.assertj.core.groups.Tuple.tuple(12L, BillingRunItemStatus.FAILED));
    }

    @Test
    void theCreditAllocationIsNotApplicableUnlessTheAutoApplySettingIsOn() {
        AccountCreditAllocationBillingRunStep step = new AccountCreditAllocationBillingRunStep(mock(StudentCurrentAccountRepository.class), mock(BillingOperationPipeline.class),
                mock(AccountCreditAllocationOperation.class), configurationReader);
        when(configurationReader.findAllocationSettingsEffectiveOn(NIGHT)).thenReturn(Optional.of(new AllocationSettings(false)));
        when(configurationReader.findAllocationSettingsEffectiveOn(NIGHT.plusDays(1))).thenReturn(Optional.empty());
        when(configurationReader.findAllocationSettingsEffectiveOn(NIGHT.plusDays(2))).thenReturn(Optional.of(new AllocationSettings(true)));

        assertThat(step.findNotApplicableCode(NIGHT)).contains("AUTO_APPLY_CREDIT_DISABLED");
        assertThat(step.findNotApplicableCode(NIGHT.plusDays(1))).contains("AUTO_APPLY_CREDIT_DISABLED");
        assertThat(step.findNotApplicableCode(NIGHT.plusDays(2))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void anAccountWhoseAllocationFailsIsReportedAndTheNextOneIsStillProcessed() {
        StudentCurrentAccountRepository accounts = mock(StudentCurrentAccountRepository.class);
        BillingOperationPipeline pipeline = mock(BillingOperationPipeline.class);
        AccountCreditAllocationOperation operation = mock(AccountCreditAllocationOperation.class);
        when(accounts.findIdsWithUnallocatedCreditAfter(eq(0L), any(Pageable.class))).thenReturn(List.of(1L, 2L));
        when(accounts.findIdsWithUnallocatedCreditAfter(eq(2L), any(Pageable.class))).thenReturn(List.of());
        when(pipeline.runOperation(any(), any())).thenThrow(new IllegalStateException("lock timeout")).thenReturn((BillingOperationOutcome) new BillingOperationOutcome.Processed<>("done"));

        BillingRunStepOutcome outcome = new AccountCreditAllocationBillingRunStep(accounts, pipeline, operation, configurationReader).runStepForBusinessDate(NIGHT);

        assertThat(outcome.candidates()).isEqualTo(2);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.processed()).isEqualTo(1);
        assertThat(outcome.items()).singleElement().satisfies(item -> {
            assertThat(item.subjectType()).isEqualTo(BillingRunSubjectType.CURRENT_ACCOUNT);
            assertThat(item.subjectId()).isEqualTo(1L);
        });
    }
}
