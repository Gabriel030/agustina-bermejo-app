package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.ProbeContext;
import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.ProbeEvent;
import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.RecordingOperation;
import com.red_escuela.backend.billing.operation.RecordingOperationFixtures.RejectedContextException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingOperationPipelineTest {
    private static final ProbeContext CONTEXT = new ProbeContext("a");

    private final List<String> calls = new ArrayList<>();
    private final List<Object> publishedEvents = new ArrayList<>();
    private final List<PriorAuthorization> seenAuthorizations = new ArrayList<>();
    private final List<Object[]> openedRequests = new ArrayList<>();
    private RecordingOperation operation;

    @BeforeEach
    void setUp() {
        operation = new RecordingOperation(calls);
    }

    private BillingOperationPipeline pipelineWith(PreProcessingHandler<?>... handlers) {
        OperationHandlerRegistry registry = new OperationHandlerRegistry(
                List.of(new OperationPreHandlers(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS, List.of(handlers))));
        AuthorizationRequestPort port = (type, context, role, reason, expiresOn) -> {
            calls.add("openRequest");
            openedRequests.add(new Object[]{type, context, role, reason, expiresOn});
            return 77L;
        };
        return new BillingOperationPipeline(registry, port, event -> {
            calls.add("publish");
            publishedEvents.add(event);
        });
    }

    @Test
    @Tag("D-051")
    void stagesRunInFixedOrder() {
        operation.events = 2;
        BillingOperationPipeline pipeline = pipelineWith(
                RecordingOperationFixtures.handler(calls, "pre1", PreProcessingResult.proceed(), seenAuthorizations),
                RecordingOperationFixtures.handler(calls, "pre2", PreProcessingResult.proceed(), seenAuthorizations));

        BillingOperationOutcome<String> outcome = pipeline.runOperation(operation, CONTEXT);

        assertThat(calls).containsExactly("validate", "pre1", "pre2", "process", "events", "publish", "publish");
        assertThat(outcome).isEqualTo(new BillingOperationOutcome.Processed<>("processed-a"));
        assertThat(publishedEvents).hasSize(2).allSatisfy(event -> assertThat(event).isInstanceOf(ProbeEvent.class));
        assertThat(seenAuthorizations).containsOnly(PriorAuthorization.ABSENT);
    }

    @Test
    @Tag("D-051")
    @Tag("D-096")
    void validationDenialStopsBeforePreProcessing() {
        operation.rejectContext = true;
        BillingOperationPipeline pipeline = pipelineWith(
                RecordingOperationFixtures.handler(calls, "pre1", PreProcessingResult.proceed(), seenAuthorizations));

        assertThatThrownBy(() -> pipeline.runOperation(operation, CONTEXT)).isInstanceOf(RejectedContextException.class);

        assertThat(calls).containsExactly("validate");
        assertThat(publishedEvents).isEmpty();
        assertThat(openedRequests).isEmpty();
    }

    @Test
    @Tag("D-051")
    void authorizationRequiredStopsBeforeProcessing() {
        BillingOperationPipeline pipeline = pipelineWith(
                RecordingOperationFixtures.handler(calls, "pre1", PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "needs director"), seenAuthorizations),
                RecordingOperationFixtures.handler(calls, "pre2", PreProcessingResult.proceed(), seenAuthorizations));

        BillingOperationOutcome<String> outcome = pipeline.runOperation(operation, CONTEXT);

        assertThat(outcome).isEqualTo(new BillingOperationOutcome.AwaitingAuthorization<String>(77L));
        assertThat(calls).containsExactly("validate", "pre1", "openRequest");
        assertThat(publishedEvents).isEmpty();
        assertThat(openedRequests).singleElement().satisfies(opened -> {
            assertThat(opened[0]).isEqualTo(BillingOperationType.CREATE_LATE_PAYMENT_SETTINGS);
            assertThat(opened[1]).isSameAs(CONTEXT);
            assertThat(opened[2]).isEqualTo(AuthorizationRole.DIRECTOR);
            assertThat(opened[3]).isEqualTo("needs director");
            assertThat(opened[4]).isNull();
        });
    }

    @Test
    @Tag("D-051")
    void authorizationExpiryIsHandedToTheRequest() {
        LocalDate expiry = LocalDate.of(2026, 12, 1);
        BillingOperationPipeline pipeline = pipelineWith(RecordingOperationFixtures.handler(calls, "pre1",
                PreProcessingResult.requireAuthorizationUntil(AuthorizationRole.DIRECTOR_OR_ADMINISTRATIVE, "r", expiry), seenAuthorizations));

        pipeline.runOperation(operation, CONTEXT);

        assertThat(openedRequests.get(0)[4]).isEqualTo(expiry);
        assertThat(openedRequests.get(0)[2]).isEqualTo(AuthorizationRole.DIRECTOR_OR_ADMINISTRATIVE);
    }

    @Test
    @Tag("D-051")
    void resumingAnApprovedOperationRevalidatesAndLetsHandlersSeeTheApproval() {
        BillingOperationPipeline pipeline = pipelineWith(
                RecordingOperationFixtures.handler(calls, "pre1", PreProcessingResult.proceed(), seenAuthorizations));

        BillingOperationOutcome<String> outcome = pipeline.resumeAuthorizedOperation(operation, CONTEXT);

        assertThat(outcome).isEqualTo(new BillingOperationOutcome.Processed<>("processed-a"));
        assertThat(calls).containsExactly("validate", "pre1", "process", "events", "publish");
        assertThat(seenAuthorizations).containsExactly(PriorAuthorization.APPROVED);
    }

    @Test
    @Tag("D-051")
    void resumingAnApprovedOperationStillStopsWhenValidationFailsNow() {
        operation.rejectContext = true;
        BillingOperationPipeline pipeline = pipelineWith();

        assertThatThrownBy(() -> pipeline.resumeAuthorizedOperation(operation, CONTEXT)).isInstanceOf(RejectedContextException.class);

        assertThat(calls).containsExactly("validate");
    }

    @Test
    @Tag("D-051")
    void anOperationWithoutPreHandlersGoesStraightToProcessing() {
        BillingOperationPipeline pipeline = new BillingOperationPipeline(new OperationHandlerRegistry(List.of()),
                (type, context, role, reason, expiresOn) -> 1L, publishedEvents::add);

        pipeline.runOperation(operation, CONTEXT);

        assertThat(calls).containsExactly("validate", "process", "events");
        assertThat(publishedEvents).hasSize(1);
    }

    private PreProcessingHandler<ProbeContext> hookedHandler(String name, PreProcessingResult result) {
        return new PreProcessingHandler<>() {
            @Override
            public PreProcessingResult handleBeforeProcessing(BillingOperationType type, ProbeContext context, PriorAuthorization prior) {
                calls.add(name + ":before");
                return result;
            }

            @Override
            public void handleAfterProcessing(BillingOperationType type, ProbeContext context, Object processed) {
                calls.add(name + ":after:" + processed);
            }

            @Override
            public void handleAwaitingAuthorization(BillingOperationType type, ProbeContext context, Long authorizationRequestId) {
                calls.add(name + ":awaiting:" + authorizationRequestId);
            }
        };
    }

    @Test
    @Tag("D-136")
    void everyHandlerIsToldTheResultAfterProcessingAndBeforeTheEventsArePublished() {
        BillingOperationPipeline pipeline = pipelineWith(hookedHandler("first", PreProcessingResult.proceed()), hookedHandler("second", PreProcessingResult.proceed()));

        pipeline.runOperation(operation, CONTEXT);

        assertThat(calls).containsExactly("validate", "first:before", "second:before", "process", "first:after:processed-a", "second:after:processed-a", "events", "publish");
    }

    @Test
    @Tag("D-136")
    void everyHandlerIsToldTheAuthorizationRequestWhenTheOperationWaitsForApproval() {
        BillingOperationPipeline pipeline = pipelineWith(hookedHandler("first", PreProcessingResult.proceed()),
                hookedHandler("second", PreProcessingResult.requireAuthorization(AuthorizationRole.DIRECTOR, "needs approval")));

        BillingOperationOutcome<String> outcome = pipeline.runOperation(operation, CONTEXT);

        assertThat(outcome).isEqualTo(new BillingOperationOutcome.AwaitingAuthorization<String>(77L));
        assertThat(calls).containsExactly("validate", "first:before", "second:before", "openRequest", "first:awaiting:77", "second:awaiting:77");
    }

    @Test
    @Tag("D-136")
    void aReplayedOutcomeIsReturnedWithoutProcessingPublishingOrTouchingLaterHandlers() {
        BillingOperationOutcome<String> stored = new BillingOperationOutcome.Processed<>("stored");
        BillingOperationPipeline pipeline = pipelineWith(hookedHandler("first", PreProcessingResult.replayStoredOutcome(stored)), hookedHandler("second", PreProcessingResult.proceed()));

        BillingOperationOutcome<String> outcome = pipeline.runOperation(operation, CONTEXT);

        assertThat(outcome).isSameAs(stored);
        assertThat(calls).containsExactly("validate", "first:before");
        assertThat(publishedEvents).isEmpty();
    }

    @Test
    @Tag("D-136")
    void aReplayedAwaitingAuthorizationOutcomeIsReturnedAsTheOriginalOne() {
        BillingOperationOutcome<String> stored = new BillingOperationOutcome.AwaitingAuthorization<>(55L);
        BillingOperationPipeline pipeline = pipelineWith(hookedHandler("first", PreProcessingResult.replayStoredOutcome(stored)));

        assertThat(pipeline.runOperation(operation, CONTEXT)).isEqualTo(new BillingOperationOutcome.AwaitingAuthorization<String>(55L));
        assertThat(calls).doesNotContain("openRequest", "process");
    }

    @Test
    @Tag("D-136")
    void aReplayedOutcomeMustCarryAnOutcome() {
        assertThatThrownBy(() -> PreProcessingResult.replayStoredOutcome(null)).isInstanceOf(NullPointerException.class);
    }
}
