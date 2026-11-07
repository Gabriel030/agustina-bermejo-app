package com.red_escuela.backend.billing.annualupfront.controller;

import tools.jackson.databind.JsonNode;
import com.red_escuela.backend.billing.annualupfront.repository.AnnualUpfrontRequestRepository;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontDeadlineExpirer;
import com.red_escuela.backend.billing.annualupfront.service.AnnualUpfrontRequestDeadlineDiscarder;
import com.red_escuela.backend.billing.run.domain.BillingRunItemStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunSubjectType;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.dto.BillingRunStepResponse;
import com.red_escuela.backend.billing.run.dto.NightlyBillingRunResponse;
import com.red_escuela.backend.billing.run.operation.AnnualUpfrontExpiryBillingRunStep;
import com.red_escuela.backend.billing.run.policy.PreviousStepContext;
import com.red_escuela.backend.billing.run.service.BillingRunAlertPublisher;
import com.red_escuela.backend.billing.run.service.BillingRunRecorder;
import com.red_escuela.backend.billing.run.service.BillingRunStepSequence;
import com.red_escuela.backend.billing.run.service.NightlyBillingRunner;
import com.red_escuela.backend.billing.run.usecase.BillingRunQueryUseCase;
import com.red_escuela.backend.billing.invoice.operation.IssuingScenarioFactory;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.ProcessedEffectGuard;
import com.red_escuela.backend.shared.time.MutableClock;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;

@AutoConfigureMockMvc
@Tag("D-155")
@Tag("D-447")
@Tag("D-449")
@Tag("D-451")
class AnnualUpfrontNightlyExpiryStepIT extends AbstractAnnualUpfrontRequestIT {
    private static final LocalDateTime AFTER_DEADLINE = LocalDateTime.of(2027, 3, 4, 10, 1);
    private static final LocalDate FIRST_NIGHT = LocalDate.of(2027, 3, 4);
    private static final LocalDate SECOND_NIGHT = LocalDate.of(2027, 3, 5);
    private static final LocalDate THIRD_NIGHT = LocalDate.of(2027, 3, 6);
    private static final LocalDate FOURTH_NIGHT = LocalDate.of(2027, 3, 7);
    private static final LocalDate FIFTH_NIGHT = LocalDate.of(2027, 3, 8);

    @Autowired
    private AnnualUpfrontExpiryBillingRunStep expiryStep;
    @Autowired
    private AnnualUpfrontRequestRepository requestRepository;
    @Autowired
    private AnnualUpfrontRequestDeadlineDiscarder realDiscarder;
    @Autowired
    private BillingRunRecorder recorder;
    @Autowired
    private PolicySet<PreviousStepContext> stepSequencePolicySet;
    @Autowired
    private ProcessedEffectGuard effectGuard;
    @Autowired
    private BillingRunQueryUseCase queryUseCase;

    private NightlyBillingRunner runnerWith(AnnualUpfrontExpiryBillingRunStep step) {
        return new NightlyBillingRunner(new BillingRunStepSequence(List.of(step)), recorder, stepSequencePolicySet,
                new BillingRunAlertPublisher(Mockito.mock(ApplicationEventPublisher.class), effectGuard));
    }

    private NightlyBillingRunResponse runNight(NightlyBillingRunner runner, LocalDate night) {
        return queryUseCase.findNightlyBillingRun(runner.runNightlyBillingSteps(night, NightlyBillingTrigger.SCHEDULED).orElseThrow());
    }

    private BillingRunStepResponse onlyStepOf(NightlyBillingRunResponse run) {
        assertThat(run.steps()).hasSize(1);
        assertThat(run.steps().get(0).step()).isEqualTo(BillingRunStep.ANNUAL_UPFRONT_EXPIRY);
        return run.steps().get(0);
    }

    private JsonNode requestTheYear() throws Exception {
        return objectMapper.readTree(request(true, "v1", UUID.randomUUID().toString(), studentToken(), 201).getResponse().getContentAsString());
    }

    private JsonNode requestTheYearOf(IssuingScenarioFactory.Learner someLearner) throws Exception {
        return objectMapper.readTree(requestOf(someLearner, true, "v1", UUID.randomUUID().toString(), studentTokenOf(someLearner), 201).getResponse().getContentAsString());
    }

    private long requestsInStatus(long requestId, String status) {
        return count("select count(*) from annual_upfront_request where id = " + requestId + " and status = '" + status + "'");
    }

    @Test
    void aRequestWithoutPaymentPastItsDeadlineIsDiscardedByTheNightAndReleasesEverythingItHeld() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = requestTheYear();
        long requestId = requested.get("id").asLong();
        long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(AFTER_DEADLINE);

        NightlyBillingRunResponse run = runNight(runnerWith(expiryStep), FIRST_NIGHT);

        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED);
        BillingRunStepResponse step = onlyStepOf(run);
        assertThat(step.status()).isEqualTo(BillingRunStatus.COMPLETED);
        assertThat(step.candidates()).isEqualTo(1);
        assertThat(step.processed()).isEqualTo(1);
        assertThat(step.failed()).isZero();
        assertThat(count("select count(*) from annual_upfront_request where id = " + requestId + " and status = 'DISCARDED' and discard_reason = 'DEADLINE_EXPIRED' and decided_at is not null"))
                .isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + invoiceId + " and document_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from invoice_line where invoice_id = " + invoiceId + " and charge_key_released_at is null")).isZero();
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'ACTIVE' and program_enrollment_id = " + learner.programEnrollmentId())).isZero();
        assertThat(count("select count(*) from billing_run_item where billing_run_id = " + step.id())).isZero();
    }

    @Test
    void aRequestWithPaymentAlreadyStartedIsNeverTouchedByTheNight() throws Exception {
        JsonNode requested = requestTheYear();
        long requestId = requested.get("id").asLong();
        jdbc.update("update annual_upfront_request set status = 'PAYMENT_UNDER_REVIEW' where id = ?", requestId);
        MutableClock.INSTANCE.setDateTime(LocalDateTime.of(2027, 3, 9, 10, 0));

        NightlyBillingRunResponse run = runNight(runnerWith(expiryStep), THIRD_NIGHT);

        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED);
        assertThat(onlyStepOf(run).candidates()).isZero();
        assertThat(requestsInStatus(requestId, "PAYMENT_UNDER_REVIEW")).isEqualTo(1);
        assertThat(count("select count(*) from invoice where id = " + requested.get("invoiceId").asLong() + " and document_status = 'AWAITING_PAYMENT'")).isEqualTo(1);
    }

    @Test
    void theSameRequestIsDiscardedOnlyOnceWhenTheNightRunsAgainTheNextDay() throws Exception {
        publishAnnualPromotion();
        JsonNode requested = requestTheYear();
        long invoiceId = requested.get("invoiceId").asLong();
        MutableClock.INSTANCE.setDateTime(AFTER_DEADLINE);
        NightlyBillingRunner runner = runnerWith(expiryStep);

        BillingRunStepResponse first = onlyStepOf(runNight(runner, SECOND_NIGHT));
        BillingRunStepResponse second = onlyStepOf(runNight(runner, FIFTH_NIGHT));

        assertThat(first.processed()).isEqualTo(1);
        assertThat(second.candidates()).isZero();
        assertThat(second.processed()).isZero();
        assertThat(count("select count(*) from invoice_status_change where invoice_id = " + invoiceId + " and to_status = 'DISCARDED'")).isEqualTo(1);
        assertThat(count("select count(*) from student_discount where origin = 'ANNUAL_UPFRONT_REQUEST' and status = 'REVOKED' and program_enrollment_id = " + learner.programEnrollmentId()))
                .isEqualTo(1);
    }

    @Test
    void aRequestThatFailsIsReportedAsAFailedItemWhileTheOthersAreStillDiscardedAndTheNightEndsWithErrors() throws Exception {
        publishAnnualPromotion();
        IssuingScenarioFactory.Learner other = newCohortLearner();
        long brokenId = requestTheYear().get("id").asLong();
        long healthyId = requestTheYearOf(other).get("id").asLong();
        AnnualUpfrontRequestDeadlineDiscarder discarder = Mockito.mock(AnnualUpfrontRequestDeadlineDiscarder.class);
        Mockito.when(discarder.discardRequestIfDeadlineExpiredWithoutPayment(anyLong())).thenAnswer(call -> {
            Long requestId = call.getArgument(0);
            if (requestId == brokenId) {
                throw new IllegalStateException("simulated failure");
            }
            return realDiscarder.discardRequestIfDeadlineExpiredWithoutPayment(requestId);
        });
        AnnualUpfrontExpiryBillingRunStep failingStep = new AnnualUpfrontExpiryBillingRunStep(new AnnualUpfrontDeadlineExpirer(requestRepository, discarder, MutableClock.INSTANCE));
        MutableClock.INSTANCE.setDateTime(AFTER_DEADLINE);

        NightlyBillingRunResponse run = runNight(runnerWith(failingStep), FOURTH_NIGHT);

        assertThat(run.status()).isEqualTo(NightlyBillingRunStatus.COMPLETED_WITH_ERRORS);
        BillingRunStepResponse step = onlyStepOf(run);
        assertThat(step.status()).isEqualTo(BillingRunStatus.COMPLETED_WITH_ERRORS);
        assertThat(step.candidates()).isEqualTo(2);
        assertThat(step.processed()).isEqualTo(1);
        assertThat(step.failed()).isEqualTo(1);
        assertThat(queryUseCase.listBillingRunItems(run.id(), BillingRunStep.ANNUAL_UPFRONT_EXPIRY))
                .extracting(item -> item.subjectType(), item -> item.subjectId(), item -> item.status(), item -> item.errorCode())
                .containsExactly(org.assertj.core.api.Assertions.tuple(BillingRunSubjectType.ANNUAL_UPFRONT_REQUEST, brokenId, BillingRunItemStatus.FAILED,
                        AnnualUpfrontExpiryBillingRunStep.ANNUAL_UPFRONT_EXPIRY_FAILED));
        assertThat(requestsInStatus(healthyId, "DISCARDED")).isEqualTo(1);
        assertThat(requestsInStatus(brokenId, "AWAITING_PAYMENT")).isEqualTo(1);
    }
}
