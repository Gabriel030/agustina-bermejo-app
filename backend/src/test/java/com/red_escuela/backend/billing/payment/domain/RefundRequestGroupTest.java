package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.shared.domain.policy.PolicyViolation;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-159")
class RefundRequestGroupTest {

    private static Refund partOf(String amount, RefundStatus status) {
        Refund refund = Refund.requestPartOf(1L, 10L, RefundFixtures.part(5L, PaymentMethodType.CASH, amount), 7L, null, RefundFixtures.REQUESTED_ON);
        switch (status) {
            case REQUESTED -> {
            }
            case APPROVED -> refund.approve(RefundFixtures.REQUESTED_ON, RefundFixtures.ACTOR);
            case COMPLETED -> {
                refund.approve(RefundFixtures.REQUESTED_ON, RefundFixtures.ACTOR);
                refund.complete(RefundFixtures.REQUESTED_ON, "REF", RefundFixtures.ACTOR);
            }
            case FAILED -> {
                refund.approve(RefundFixtures.REQUESTED_ON, RefundFixtures.ACTOR);
                refund.fail("banco", RefundFixtures.ACTOR);
            }
            case REJECTED -> refund.reject("no", RefundFixtures.ACTOR);
        }
        return refund;
    }

    @Test
    void theTotalsSplitTheAmountByCompletedAndPendingAndLeaveFailedAndRejectedPartsOut() {
        RefundRequestGroup group = new RefundRequestGroup(1L, 99L, RefundFixtures.REQUESTED_ON, List.of(
                partOf("100.00", RefundStatus.COMPLETED), partOf("20.00", RefundStatus.REQUESTED), partOf("30.00", RefundStatus.APPROVED),
                partOf("7.00", RefundStatus.FAILED), partOf("3.00", RefundStatus.REJECTED)));

        assertThat(group.totalAmount()).isEqualTo(PaymentFixtures.ars("160.00").value());
        assertThat(group.completedAmount()).isEqualTo(PaymentFixtures.ars("100.00").value());
        assertThat(group.pendingAmount()).isEqualTo(PaymentFixtures.ars("50.00").value());
    }

    @Test
    void aGroupWithoutPartsIsRejected() {
        assertThatThrownBy(() -> new RefundRequestGroup(1L, null, RefundFixtures.REQUESTED_ON, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anOutcomeWithoutViolationsIsAllowedAndReturnsItsParts() {
        RefundPart part = RefundFixtures.part(5L, PaymentMethodType.CASH, "10.00");
        RefundPlanOutcome outcome = RefundPlanOutcome.planned(List.of(part));

        assertThat(outcome.isAllowed()).isTrue();
        assertThat(outcome.partsOrThrow(violations -> new IllegalStateException("unexpected"))).containsExactly(part);
    }

    @Test
    void anOutcomeWithViolationsIsNotAllowedAndThrowsTheMappedExceptionEvenIfItKeepsParts() {
        RefundPart part = RefundFixtures.part(5L, PaymentMethodType.CASH, "10.00");
        PolicyViolation violation = new PolicyViolation("REFUND_DESTINATION_REQUIRED", "falta destino");
        RefundPlanOutcome outcome = RefundPlanOutcome.plannedButBlocked(List.of(part), List.of(violation));

        assertThat(outcome.isAllowed()).isFalse();
        assertThat(outcome.parts()).containsExactly(part);
        assertThatThrownBy(() -> outcome.partsOrThrow(violations -> new IllegalStateException(violations.get(0).code()))).hasMessage("REFUND_DESTINATION_REQUIRED");
        assertThat(RefundPlanOutcome.blocked(List.of(violation)).parts()).isEmpty();
    }
}
