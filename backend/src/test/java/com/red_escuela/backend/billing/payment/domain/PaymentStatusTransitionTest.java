package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.statemachine.ExhaustiveTransitionExpectations;
import com.red_escuela.backend.shared.domain.statemachine.StateTransitionRow;
import com.red_escuela.backend.shared.domain.statemachine.TransitionExpectation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.red_escuela.backend.billing.payment.domain.PaymentEvent.*;
import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ACTOR;
import static com.red_escuela.backend.billing.payment.domain.PaymentStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-082")
@Tag("D-094")
@ExhaustiveTransitionExpectations(machine = PaymentStatusStateMachine.class)
class PaymentStatusTransitionTest {

    private record Row(PaymentStatus from, PaymentEvent event, PaymentStatus to) {
    }

    private static final Set<Row> SPEC_TABLE = Set.of(
            new Row(null, CASH_PAYMENT_REGISTERED, APPROVED),
            new Row(null, PENDING_ONLINE_PAYMENT_CREATED, PENDING),
            new Row(null, PENDING_BANK_TRANSFER_CREATED, PENDING),
            new Row(PENDING, PAYMENT_HOLD_AUTHORIZED, AUTHORIZED),
            new Row(PENDING, PAYMENT_APPROVED, APPROVED),
            new Row(AUTHORIZED, PAYMENT_APPROVED, APPROVED),
            new Row(PENDING, PAYMENT_REJECTED, REJECTED),
            new Row(PENDING, PENDING_PAYMENT_CANCELLED, CANCELLED),
            new Row(AUTHORIZED, PENDING_PAYMENT_CANCELLED, CANCELLED),
            new Row(APPROVED, PARTIAL_REFUND_REGISTERED, APPROVED),
            new Row(APPROVED, FULL_REFUND_REGISTERED, REFUNDED),
            new Row(APPROVED, CHARGEBACK_REGISTERED, CHARGED_BACK),
            new Row(CHARGED_BACK, CHARGEBACK_REVERSED, APPROVED),
            new Row(APPROVED, BANK_TRANSFER_APPROVAL_REVOKED, REJECTED));

    static List<TransitionExpectation> expectedTransitions() {
        List<PaymentStatus> states = new ArrayList<>();
        states.add(null);
        states.addAll(EnumSet.allOf(PaymentStatus.class));
        return states.stream().flatMap(from -> EnumSet.allOf(PaymentEvent.class).stream()
                .map(event -> expectedTarget(from, event)
                        .map(to -> TransitionExpectation.allows(from, event, to))
                        .orElseGet(() -> TransitionExpectation.rejects(from, event, IllegalPaymentStatusTransitionException.class)))).toList();
    }

    static Stream<Row> everyStatusAndEventPair() {
        List<PaymentStatus> states = new ArrayList<>(EnumSet.allOf(PaymentStatus.class));
        return states.stream().flatMap(from -> EnumSet.allOf(PaymentEvent.class).stream()
                .map(event -> new Row(from, event, expectedTarget(from, event).orElse(null))));
    }

    private static Optional<PaymentStatus> expectedTarget(PaymentStatus from, PaymentEvent event) {
        return SPEC_TABLE.stream().filter(row -> row.from() == from && row.event() == event).map(Row::to).findFirst();
    }

    private static Payment paymentIn(PaymentStatus status) {
        Payment payment = PaymentFixtures.pendingBankTransfer();
        switch (status) {
            case PENDING -> {
            }
            case AUTHORIZED -> payment.changeStatus(PAYMENT_HOLD_AUTHORIZED, ACTOR, null, GatewayRawStatus.none());
            case APPROVED -> payment.changeStatus(PAYMENT_APPROVED, ACTOR, null, GatewayRawStatus.none());
            case REJECTED -> payment.changeStatus(PAYMENT_REJECTED, ACTOR, null, GatewayRawStatus.none());
            case CANCELLED -> payment.changeStatus(PENDING_PAYMENT_CANCELLED, ACTOR, null, GatewayRawStatus.none());
            case REFUNDED -> {
                payment.changeStatus(PAYMENT_APPROVED, ACTOR, null, GatewayRawStatus.none());
                payment.changeStatus(FULL_REFUND_REGISTERED, ACTOR, null, GatewayRawStatus.none());
            }
            case CHARGED_BACK -> {
                payment.changeStatus(PAYMENT_APPROVED, ACTOR, null, GatewayRawStatus.none());
                payment.changeStatus(CHARGEBACK_REGISTERED, ACTOR, null, GatewayRawStatus.none());
            }
        }
        return payment;
    }

    @Test
    void theTransitionTableIsExactlyTheOneInTheSpecification() {
        Set<Row> actual = PaymentStatusStateMachine.TRANSITIONS.listAllowedTransitions().stream()
                .map((StateTransitionRow<PaymentStatus, PaymentEvent> row) -> new Row(row.fromState(), row.event(), row.toState()))
                .collect(Collectors.toSet());

        assertThat(actual).isEqualTo(SPEC_TABLE);
    }

    @ParameterizedTest
    @MethodSource("everyStatusAndEventPair")
    void eachStatusAndEventPairEitherMovesAsTheTableSaysOrIsRejectedWithoutChangingAnything(Row pair) {
        Payment payment = paymentIn(pair.from());
        int changesBefore = payment.statusChanges().size();

        if (pair.to() == null) {
            assertThatThrownBy(() -> payment.changeStatus(pair.event(), ACTOR, "reason", new GatewayRawStatus("raw", "detail")))
                    .isInstanceOf(IllegalPaymentStatusTransitionException.class);
            assertThat(payment.getStatus()).isEqualTo(pair.from());
            assertThat(payment.statusChanges()).hasSize(changesBefore);
            return;
        }
        payment.changeStatus(pair.event(), ACTOR, "reason", new GatewayRawStatus("raw", "detail"));

        assertThat(payment.getStatus()).isEqualTo(pair.to());
        assertThat(payment.statusChanges()).hasSize(changesBefore + 1);
        PaymentStatusChange written = payment.statusChanges().get(changesBefore);
        assertThat(written.getFromStatus()).isEqualTo(pair.from().name());
        assertThat(written.getToStatus()).isEqualTo(pair.to().name());
        assertThat(written.getEvent()).isEqualTo(pair.event().name());
        assertThat(written.getReason()).isEqualTo("reason");
        assertThat(written.getGatewayRawStatus()).isEqualTo("raw");
        assertThat(written.getGatewayRawStatusDetail()).isEqualTo("detail");
        assertThat(written.getActorPersonId()).isEqualTo(ACTOR.personId());
        assertThat(payment.getGatewayRawStatus()).isEqualTo("raw");
        assertThat(written.getOccurredAt()).isNotNull();
    }

    @Test
    void terminalStatusesAcceptNoEvent() {
        for (PaymentStatus terminal : List.of(REJECTED, CANCELLED, REFUNDED)) {
            for (PaymentEvent event : PaymentEvent.values()) {
                assertThatThrownBy(() -> paymentIn(terminal).changeStatus(event, ACTOR, null, GatewayRawStatus.none()))
                        .isInstanceOf(IllegalPaymentStatusTransitionException.class);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("initialEvents")
    void theFirstEventOfAPaymentIsOnlyOneOfTheThreeCreationEvents(PaymentEvent event) {
        Optional<PaymentStatus> expected = expectedTarget(null, event);

        assertThat(PaymentStatusStateMachine.TRANSITIONS.findTargetStateFor(null, event)).isEqualTo(expected);
        if (expected.isEmpty()) {
            assertThatThrownBy(() -> PaymentStatusStateMachine.resolveNextStatus(null, event)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        }
    }

    static Stream<PaymentEvent> initialEvents() {
        return EnumSet.allOf(PaymentEvent.class).stream();
    }

    @Test
    void theCreationFactoriesWriteTheFirstHistoryRowWithoutPreviousStatus() {
        Payment cash = PaymentFixtures.cashPayment();
        Payment transfer = PaymentFixtures.pendingBankTransfer();
        Payment gateway = PaymentFixtures.pendingGatewayPayment(PaymentGatewayCode.MERCADO_PAGO);

        assertThat(cash.getStatus()).isEqualTo(APPROVED);
        assertThat(transfer.getStatus()).isEqualTo(PENDING);
        assertThat(gateway.getStatus()).isEqualTo(PENDING);
        assertThat(cash.statusChanges()).singleElement().satisfies(change -> {
            assertThat(change.getFromStatus()).isNull();
            assertThat(change.getEvent()).isEqualTo("CASH_PAYMENT_REGISTERED");
        });
        assertThat(gateway.statusChanges().get(0).getGatewayRawStatus()).isEqualTo("pending");
        assertThat(gateway.statusChanges().get(0).getGatewayRawStatusDetail()).isEqualTo("pending_waiting_payment");
        assertThat(gateway.getGatewayRawStatus()).isEqualTo("pending");
    }
}
