package com.red_escuela.backend.notification.delivery.domain;

import com.red_escuela.backend.notification.delivery.entity.NotificationDelivery;
import com.red_escuela.backend.notification.delivery.exception.IllegalNotificationDeliveryStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.red_escuela.backend.notification.delivery.domain.DeliveryEvent.*;
import static com.red_escuela.backend.notification.delivery.domain.DeliveryStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("c08")
@ExhaustiveTransitionExpectations(machine = DeliveryStatusStateMachine.class)
class DeliveryStatusTransitionTest {
    private static final Instant NOW = Instant.parse("2026-03-09T12:00:00Z");
    private static final Class<IllegalNotificationDeliveryStatusTransitionException> ILLEGAL = IllegalNotificationDeliveryStatusTransitionException.class;
    private static final DeliveryEnvelope ENVELOPE = new DeliveryEnvelope("reminder:1:2026-03-09", DeliveryCategory.COLLECTION_REMINDER,
            "BEFORE_DUE_REMINDER", 3, 7L, "Invoice", 11L, LocalDate.of(2026, 3, 9));
    private static final DeliveryContent EMAIL = DeliveryContent.forEmail("Asunto", "Texto", "<p>Texto</p>");

    private static final Map<DeliveryStatus, Map<DeliveryEvent, DeliveryStatus>> ALLOWED = Map.of(
            QUEUED, Map.of(SEND_VIA_PROVIDER, SENT, SEND_VIA_SIMULATOR, SENT_SIMULATED, SUPPRESS, SUPPRESSED, DEFER, DEFERRED, DISCARD, DISCARDED, GIVE_UP, FAILED),
            DEFERRED, Map.of(SEND_VIA_PROVIDER, SENT, SEND_VIA_SIMULATOR, SENT_SIMULATED, SUPPRESS, SUPPRESSED, DEFER, DEFERRED, DISCARD, DISCARDED, GIVE_UP, FAILED));
    private static final Map<DeliveryEvent, DeliveryStatus> INITIAL = Map.of(QUEUE_FOR_DISPATCH, QUEUED, DELIVER_IN_APP, SENT, SUPPRESS, SUPPRESSED);

    static List<TransitionExpectation> expectedTransitions() {
        List<TransitionExpectation> expectations = new ArrayList<>();
        for (DeliveryEvent event : DeliveryEvent.values()) {
            expectations.add(INITIAL.containsKey(event) ? TransitionExpectation.allows(null, event, INITIAL.get(event)) : TransitionExpectation.rejects(null, event, ILLEGAL));
        }
        for (DeliveryStatus from : DeliveryStatus.values()) {
            for (DeliveryEvent event : DeliveryEvent.values()) {
                DeliveryStatus target = ALLOWED.getOrDefault(from, Map.of()).get(event);
                expectations.add(target != null ? TransitionExpectation.allows(from, event, target) : TransitionExpectation.rejects(from, event, ILLEGAL));
            }
        }
        return expectations;
    }

    static Stream<TransitionExpectation> everyPair() {
        return expectedTransitions().stream();
    }

    private static NotificationDelivery deliveryIn(DeliveryStatus status) {
        NotificationDelivery delivery = NotificationDelivery.queuedEmail(ENVELOPE, EMAIL, NOW);
        switch (status) {
            case QUEUED -> {
            }
            case SENT -> delivery = NotificationDelivery.deliveredInApp(ENVELOPE, DeliveryContent.forInApp("Mensaje"), NOW);
            case SENT_SIMULATED -> delivery.markSent("mock-1", true, NOW);
            case SUPPRESSED -> delivery = NotificationDelivery.suppressedEmail(ENVELOPE, EMAIL, "OPTED_OUT", NOW);
            case DEFERRED -> delivery.defer("DAILY_CAP", NOW);
            case DISCARDED -> delivery.discard("NO_LONGER_TIMELY");
            case FAILED -> {
                delivery.recordFailedAttempt("PROVIDER_ERROR", 1);
            }
        }
        return delivery;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPair")
    void everyStateAndEventPairBehavesAsTheTableSays(TransitionExpectation expectation) {
        DeliveryStatus from = (DeliveryStatus) expectation.fromState();
        DeliveryEvent event = (DeliveryEvent) expectation.event();
        if (from == null) {
            assertThat(DeliveryStatusStateMachine.findTargetStateFor(null, event)).isEqualTo(Optional.ofNullable((DeliveryStatus) expectation.toState()));
            return;
        }
        NotificationDelivery delivery = deliveryIn(from);
        assertThat(delivery.getStatus()).isEqualTo(from);
        String reasonBefore = delivery.getStatusReason();
        if (expectation.isAllowed()) {
            ReflectionTestUtils.invokeMethod(delivery, "moveTo", event, "REASON");
            assertThat(delivery.getStatus()).isEqualTo(expectation.toState());
            assertThat(delivery.getStatusReason()).isEqualTo(delivery.getStatus().requiresReason() ? "REASON" : null);
        } else {
            assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(delivery, "moveTo", event, "REASON")).isInstanceOf(expectation.rejection());
            assertThat(delivery.getStatus()).isEqualTo(from);
            assertThat(delivery.getStatusReason()).isEqualTo(reasonBefore);
        }
    }

    @Test
    void theTableListsOnlyTheFifteenAllowedTransitions() {
        assertThat(DeliveryStatusStateMachine.TRANSITIONS.listAllowedTransitions()).hasSize(15);
    }

    @Test
    void anAllowedTransitionIntoAStateThatNeedsAReasonRejectsAMissingReason() {
        NotificationDelivery delivery = deliveryIn(QUEUED);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(delivery, "moveTo", DISCARD, (String) null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(delivery.getStatus()).isEqualTo(QUEUED);
    }
}
