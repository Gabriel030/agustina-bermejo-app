package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualResult;
import com.red_escuela.backend.billing.collections.operation.LateFeeAccrualStep;
import com.red_escuela.backend.billing.events.NightlyBillingRunFailedEvent;
import com.red_escuela.backend.billing.run.domain.BillingRunItemRecord;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.dto.BillingRunItemResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.operation.AbstractBillingRunStep;
import com.red_escuela.backend.billing.run.policy.PreviousStepContext;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.testsupport.SharedMockBeans;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

@SpringBootTest
@ActiveProfiles("test")
@SharedMockBeans
@Tag("D-356")
class NightlyBillingRunnerIT extends AbstractIntegrationTest {

    @Autowired
    private NightlyBillingRunner runner;
    @Autowired
    private BillingRunRecorder recorder;
    @Autowired
    private PolicySet<PreviousStepContext> stepSequencePolicySet;
    @Autowired
    private BillingRunQueryUseCase queryUseCase;
    @Autowired
    private LateFeeAccrualStep lateFeeAccrualStep;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ApplicationEventPublisher publisher = Mockito.mock(ApplicationEventPublisher.class);

    @BeforeEach
    void stubTheLateFeeAccrualOutsideThisSuite() {
        Mockito.when(lateFeeAccrualStep.accrueLateFees(any(), any())).thenReturn(LateFeeAccrualResult.nothing());
    }

    private static AbstractBillingRunStep stepReturning(BillingRunStep step, BillingRunStepOutcome outcome) {
        return new AbstractBillingRunStep() {
            @Override
            public BillingRunStep step() {
                return step;
            }

            @Override
            protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
                return outcome;
            }
        };
    }

    private static AbstractBillingRunStep stepFailingEntirely(BillingRunStep step, List<BillingRunStep> executed) {
        return new AbstractBillingRunStep() {
            @Override
            public BillingRunStep step() {
                return step;
            }

            @Override
            protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
                executed.add(step);
                throw new IllegalStateException("page selection exploded");
            }
        };
    }

    private NightlyBillingRunner runnerWith(AbstractBillingRunStep... steps) {
        return new NightlyBillingRunner(new BillingRunStepSequence(List.of(steps)), recorder, stepSequencePolicySet, publisher);
    }

    private int countOf(String table, String where, Object... args) {
        return jdbcTemplate.queryForObject("select count(*) from " + table + " where " + where, Integer.class, args);
    }

    @Test
    @Tag("D-447")
    @Tag("D-451")
    void theNightRegistersOneNightlyRunWithItsStepsInOrderAndTheGatewayNotApplicable() {
        LocalDate day = LocalDate.of(2031, 10, 7);

        Optional<Long> id = runner.runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED);

        assertThat(id).isPresent();
        NightlyBillingRunResponse run = queryUseCase.findNightlyBillingRun(id.get());
        assertThat(run.businessDate()).isEqualTo(day);
        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED);
        assertThat(run.steps()).extracting(step -> step.step()).containsExactly(BillingRunStep.ACCOUNT_CONSISTENCY, BillingRunStep.GATEWAY_SYNC, BillingRunStep.OVERDUE_DETECTION, BillingRunStep.LATE_FEE_ACCRUAL,
                BillingRunStep.LATE_FEE_WITHDRAWAL_CORRECTION, BillingRunStep.COLLECTIONS_SNAPSHOT, BillingRunStep.ENROLLMENT_BILLING_COMPLIANCE, BillingRunStep.TERM_END_WITHDRAWAL,
                BillingRunStep.ACCOUNT_CREDIT_ALLOCATION);
        assertThat(run.steps().get(1).status()).isEqualTo(BillingRunStatus.NOT_APPLICABLE);
        assertThat(run.steps().get(1).notApplicableCode()).isEqualTo("GATEWAY_DISABLED");
        assertThat(run.failedSteps()).isZero();
        assertThat(countOf("billing_run_item", "billing_run_id in (select id from billing_run where nightly_billing_run_id = ?)", id.get())).isZero();
    }

    @Test
    @Tag("D-458")
    void aSecondDispatchOfTheSameDayCreatesNothingAndProcessesNothing() {
        LocalDate day = LocalDate.of(2031, 10, 8);
        runner.runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED);
        int stepsBefore = countOf("billing_run", "nightly_billing_run_id in (select id from nightly_billing_run where business_date = ?)", day);

        Optional<Long> second = runner.runNightlyBillingSteps(day, NightlyBillingTrigger.MANUAL);

        assertThat(second).isEmpty();
        assertThat(countOf("nightly_billing_run", "business_date = ?", day)).isEqualTo(1);
        assertThat(countOf("billing_run", "nightly_billing_run_id in (select id from nightly_billing_run where business_date = ?)", day)).isEqualTo(stepsBefore);
    }

    @Test
    @Tag("D-458")
    void twoThreadsDispatchingTheSameDayLeaveASingleRunWithItsStepsOnce() throws Exception {
        LocalDate day = LocalDate.of(2031, 10, 9);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Optional<Long>>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(executor.submit(() -> {
                go.await();
                return runner.runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED);
            }));
        }
        go.countDown();
        long winners = 0;
        for (Future<Optional<Long>> result : results) {
            winners += result.get(60, TimeUnit.SECONDS).isPresent() ? 1 : 0;
        }
        executor.shutdown();

        assertThat(winners).isEqualTo(1);
        assertThat(countOf("nightly_billing_run", "business_date = ?", day)).isEqualTo(1);
        assertThat(countOf("billing_run", "nightly_billing_run_id in (select id from nightly_billing_run where business_date = ?)", day)).isEqualTo(9);
    }

    @Test
    @Tag("D-458")
    void theDatabaseRejectsASecondNightlyRunOfTheSameDay() {
        LocalDate day = LocalDate.of(2031, 10, 10);
        recorder.openNightlyRun(day, NightlyBillingTrigger.SCHEDULED);

        assertThatThrownBy(() -> recorder.openNightlyRun(day, NightlyBillingTrigger.MANUAL)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @Tag("D-451")
    void aFailedSubjectIsStoredAsAnItemAndTheStepFinishesWithErrorsWithoutStoppingTheNext() {
        LocalDate day = LocalDate.of(2031, 10, 11);
        BillingRunStepOutcome withOneFailure = BillingRunStepOutcome.ofCandidates(1200, List.of(
                BillingRunItemRecord.failedSubject(BillingRunSubjectType.INVOICE, 77L, "SUBJECT_PROCESSING_FAILED", "boom"),
                BillingRunItemRecord.skippedSubject(BillingRunSubjectType.INVOICE, 78L, "NO_LATE_PAYMENT_TERMS", "sin versión")));
        BillingRunStepOutcome clean = new BillingRunStepOutcome(10, 10, 0, 0, List.of());

        Long id = runnerWith(stepReturning(BillingRunStep.OVERDUE_DETECTION, withOneFailure), stepReturning(BillingRunStep.LATE_FEE_ACCRUAL, clean))
                .runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED).orElseThrow();

        NightlyBillingRunResponse run = queryUseCase.findNightlyBillingRun(id);
        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED_WITH_ERRORS);
        assertThat(run.steps()).extracting(step -> step.status()).containsExactly(BillingRunStatus.COMPLETED_WITH_ERRORS, BillingRunStatus.COMPLETED);
        assertThat(run.steps().get(0).processed()).isEqualTo(1198);
        assertThat(run.steps().get(0).failed()).isEqualTo(1);
        assertThat(run.steps().get(0).skipped()).isEqualTo(1);
        List<BillingRunItemResponse> items = queryUseCase.listBillingRunItems(id, BillingRunStep.OVERDUE_DETECTION);
        assertThat(items).extracting(BillingRunItemResponse::subjectId, BillingRunItemResponse::errorCode, BillingRunItemResponse::decisionCode)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(77L, "SUBJECT_PROCESSING_FAILED", null), org.assertj.core.groups.Tuple.tuple(78L, null, "NO_LATE_PAYMENT_TERMS"));
        assertThat(queryUseCase.listBillingRunItems(id, BillingRunStep.LATE_FEE_ACCRUAL)).isEmpty();
    }

    @Test
    @Tag("D-449")
    void aStepThatFailsEntirelyStopsTheNightFailsItAndRaisesTheAlertEvent() {
        LocalDate day = LocalDate.of(2031, 10, 12);
        List<BillingRunStep> executed = new ArrayList<>();
        AbstractBillingRunStep never = new AbstractBillingRunStep() {
            @Override
            public BillingRunStep step() {
                return BillingRunStep.LATE_FEE_ACCRUAL;
            }

            @Override
            protected BillingRunStepOutcome executeStepForBusinessDate(LocalDate businessDate) {
                executed.add(BillingRunStep.LATE_FEE_ACCRUAL);
                return new BillingRunStepOutcome(0, 0, 0, 0, List.of());
            }
        };

        Long id = runnerWith(stepFailingEntirely(BillingRunStep.OVERDUE_DETECTION, executed), never).runNightlyBillingSteps(day, NightlyBillingTrigger.SCHEDULED).orElseThrow();

        NightlyBillingRunResponse run = queryUseCase.findNightlyBillingRun(id);
        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.FAILED);
        assertThat(run.steps()).singleElement().satisfies(step -> assertThat(step.status()).isEqualTo(BillingRunStatus.FAILED));
        assertThat(executed).containsExactly(BillingRunStep.OVERDUE_DETECTION);
        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        Mockito.verify(publisher).publishEvent(published.capture());
        assertThat(published.getValue()).isInstanceOfSatisfying(NightlyBillingRunFailedEvent.class, event -> {
            assertThat(event.failedStep()).isEqualTo("OVERDUE_DETECTION");
            assertThat(event.businessDate()).isEqualTo(day);
            assertThat(event.nightlyBillingRunId()).isEqualTo(id);
        });
    }

    @Test
    void theNightsAreListedNewestFirstInsideTheRequestedRange() {
        runner.runNightlyBillingSteps(LocalDate.of(2032, 1, 10), NightlyBillingTrigger.SCHEDULED);
        runner.runNightlyBillingSteps(LocalDate.of(2032, 1, 11), NightlyBillingTrigger.SCHEDULED);
        runner.runNightlyBillingSteps(LocalDate.of(2032, 2, 20), NightlyBillingTrigger.SCHEDULED);

        List<NightlyBillingRunResponse> january = queryUseCase.listNightlyBillingRuns(LocalDate.of(2032, 1, 1), LocalDate.of(2032, 1, 31));

        assertThat(january).extracting(NightlyBillingRunResponse::businessDate).containsExactly(LocalDate.of(2032, 1, 11), LocalDate.of(2032, 1, 10));
        assertThat(january.get(0).steps()).hasSize(9);
    }
}
