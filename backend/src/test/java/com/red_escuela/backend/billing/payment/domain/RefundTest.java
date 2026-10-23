package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalRefundStatusTransitionException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static com.red_escuela.backend.billing.payment.domain.RefundFixtures.ACTOR;
import static com.red_escuela.backend.billing.payment.domain.RefundFixtures.REQUESTED_ON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-159")
@Tag("D-267")
class RefundTest {

    @Test
    void aRequestedPartKeepsItsAmountMethodDestinationAndOrigin() {
        Refund refund = RefundFixtures.requested();

        assertThat(refund.getAmount()).isEqualTo(PaymentFixtures.ars("1000.00"));
        assertThat(refund.getPaymentId()).isEqualTo(5L);
        assertThat(refund.getMethodType()).isEqualTo(PaymentMethodType.BANK_TRANSFER);
        assertThat(refund.getDestinationAccount()).isEqualTo(RefundFixtures.DESTINATION.account());
        assertThat(refund.getDestinationHolder()).isEqualTo(RefundFixtures.DESTINATION.holder());
        assertThat(refund.getAuthorizationRequestId()).isEqualTo(99L);
        assertThat(refund.isPending()).isTrue();
    }

    @Test
    void aPartWithoutDestinationCannotBeRequested() {
        RefundPart part = new RefundPart(5L, PaymentMethodType.CASH, PaymentFixtures.ars("10.00"), RefundDestination.none());

        assertThatThrownBy(() -> Refund.requestPartOf(1L, 10L, part, 7L, null, REQUESTED_ON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aZeroAmountPartCannotBeRequested() {
        RefundPart part = RefundFixtures.part(5L, PaymentMethodType.CASH, "0.00");

        assertThatThrownBy(() -> Refund.requestPartOf(1L, 10L, part, 7L, null, REQUESTED_ON)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void approvingRecordsTheApprovalDate() {
        Refund refund = RefundFixtures.requested();

        refund.approve(LocalDate.of(2027, 4, 2), ACTOR);

        assertThat(refund.getApprovedOn()).isEqualTo(LocalDate.of(2027, 4, 2));
        assertThat(refund.isPending()).isTrue();
    }

    @Test
    void completingRecordsDateAndReferenceAndStopsBeingPending() {
        Refund refund = RefundFixtures.in(RefundStatus.APPROVED);

        refund.complete(LocalDate.of(2027, 4, 3), "TRF-77", ACTOR);

        assertThat(refund.getCompletedOn()).isEqualTo(LocalDate.of(2027, 4, 3));
        assertThat(refund.getCompletionReference()).isEqualTo("TRF-77");
        assertThat(refund.isPending()).isFalse();
    }

    @Test
    void failingAndRejectingKeepTheirReasonAndStopBeingPending() {
        Refund failed = RefundFixtures.in(RefundStatus.APPROVED);
        failed.fail("cuenta cerrada", ACTOR);
        Refund rejected = RefundFixtures.requested();
        rejected.reject("sin fondos", ACTOR);

        assertThat(failed.getFailureReason()).isEqualTo("cuenta cerrada");
        assertThat(failed.isPending()).isFalse();
        assertThat(rejected.getRejectionReason()).isEqualTo("sin fondos");
        assertThat(rejected.isPending()).isFalse();
    }

    @Test
    void aRejectedTransitionLeavesTheFieldsUntouched() {
        Refund refund = RefundFixtures.requested();

        assertThatThrownBy(() -> refund.complete(LocalDate.of(2027, 4, 3), "TRF-77", ACTOR)).isInstanceOf(IllegalRefundStatusTransitionException.class);

        assertThat(refund.getCompletedOn()).isNull();
        assertThat(refund.getCompletionReference()).isNull();
        assertThat(refund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
    }
}
