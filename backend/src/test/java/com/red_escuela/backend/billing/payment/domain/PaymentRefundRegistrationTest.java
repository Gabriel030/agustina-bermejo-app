package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.ars;
import static com.red_escuela.backend.billing.payment.domain.RefundFixtures.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-209")
@Tag("D-159")
class PaymentRefundRegistrationTest {

    @Test
    void aPartialRefundKeepsThePaymentApprovedAndAddsToTheRefundedAmount() {
        Payment payment = RefundFixtures.approvedCash("1000.00");

        payment.registerRefundedAmount(ars("300.00"), ACTOR, "d1");
        payment.registerRefundedAmount(ars("200.00"), ACTOR, "d2");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getRefundedAmount()).isEqualTo(ars("500.00"));
        assertThat(payment.statusChanges()).extracting(PaymentStatusChange::getEvent)
                .containsExactly("CASH_PAYMENT_REGISTERED", "PARTIAL_REFUND_REGISTERED", "PARTIAL_REFUND_REGISTERED");
    }

    @Test
    void reachingTheCreditedAmountMarksThePaymentAsRefundedWithTheFullRefundEvent() {
        Payment payment = RefundFixtures.approvedCash("1000.00");
        payment.registerRefundedAmount(ars("999.99"), ACTOR, "d1");

        payment.registerRefundedAmount(ars("0.01"), ACTOR, "d2");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAmount()).isEqualTo(ars("1000.00"));
        assertThat(payment.statusChanges().get(payment.statusChanges().size() - 1).getEvent()).isEqualTo("FULL_REFUND_REGISTERED");
    }

    @Test
    void oneCentBelowTheCreditedAmountIsStillPartial() {
        Payment payment = RefundFixtures.approvedCash("1000.00");

        payment.registerRefundedAmount(ars("999.99"), ACTOR, "d1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void theRefundedAmountCanNeverExceedWhatThePaymentCredited() {
        Payment payment = RefundFixtures.approvedCash("1000.00");
        payment.registerRefundedAmount(ars("600.00"), ACTOR, "d1");

        assertThatThrownBy(() -> payment.registerRefundedAmount(ars("400.01"), ACTOR, "d2")).isInstanceOf(IllegalArgumentException.class);
        assertThat(payment.getRefundedAmount()).isEqualTo(ars("600.00"));
    }

    @Test
    void aZeroRefundIsRejected() {
        assertThatThrownBy(() -> RefundFixtures.approvedCash("1000.00").registerRefundedAmount(ars("0.00"), ACTOR, "d")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRefundInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> RefundFixtures.approvedCash("1000.00").registerRefundedAmount(NonNegativeMoney.of(new BigDecimal("1.00"), Currency.USD), ACTOR, "d"))
                .isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
    }

    @Test
    void aPaymentThatIsNotApprovedCannotBeRefunded() {
        Payment pending = PaymentFixtures.pendingBankTransfer();

        assertThatThrownBy(() -> pending.registerRefundedAmount(ars("1.00"), ACTOR, "d")).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThat(pending.getRefundedAmount()).isEqualTo(ars("0.00"));
    }

    @Test
    void aFullyRefundedPaymentAcceptsNoMoreRefunds() {
        Payment payment = RefundFixtures.approvedCash("100.00");
        payment.registerRefundedAmount(ars("100.00"), ACTOR, "d");

        assertThatThrownBy(() -> payment.registerRefundedAmount(ars("0.01"), ACTOR, "d")).isInstanceOf(IllegalArgumentException.class);
    }
}
