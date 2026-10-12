package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.payment.exception.IllegalPaymentStatusTransitionException;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.invoice.exception.StudentCurrentAccountCurrencyMismatchException;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static com.red_escuela.backend.billing.payment.domain.PaymentFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentBehaviorTest {

    @Test
    @Tag("D-082")
    @Tag("D-206")
    void onlyAnApprovedManualBankTransferCanHaveItsApprovalRevoked() {
        Payment cash = cashPayment();
        Payment card = pendingGatewayPayment(PaymentGatewayCode.MERCADO_PAGO);
        card.changeStatus(PaymentEvent.PAYMENT_APPROVED, ACTOR, null, new GatewayRawStatus("approved", "accredited"));
        Payment transfer = pendingBankTransfer();
        transfer.approvePayment(ars("80000.00"), PAID_ON, ACTOR, null);

        assertThatThrownBy(() -> cash.revokeBankTransferApproval("error", ACTOR)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThatThrownBy(() -> card.revokeBankTransferApproval("error", ACTOR)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        transfer.revokeBankTransferApproval("aprobada por error", ACTOR);

        assertThat(cash.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(card.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(transfer.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(transfer.getRejectionReason()).isEqualTo("aprobada por error");
        assertThat(transfer.statusChanges()).hasSize(3);
    }

    @Test
    @Tag("D-082")
    @Tag("D-206")
    void theStatusChangeItselfRefusesARevocationOfAnythingButAManualBankTransfer() {
        Payment cash = cashPayment();

        assertThatThrownBy(() -> cash.changeStatus(PaymentEvent.BANK_TRANSFER_APPROVAL_REVOKED, ACTOR, "x", GatewayRawStatus.none()))
                .isInstanceOf(IllegalPaymentStatusTransitionException.class);

        assertThat(cash.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(cash.statusChanges()).hasSize(1);
    }

    @Test
    @Tag("D-082")
    void aRejectedRevocationLeavesThePaymentUntouched() {
        Payment cash = cashPayment();

        assertThatThrownBy(() -> cash.revokeBankTransferApproval("error", ACTOR)).isInstanceOf(IllegalPaymentStatusTransitionException.class);

        assertThat(cash.getRejectionReason()).isNull();
        assertThat(cash.getReviewedByPersonId()).isNull();
        assertThat(cash.statusChanges()).hasSize(1);
    }

    @Test
    @Tag("D-086")
    @Tag("D-169")
    void aCashPaymentIsBornApprovedWithTheAmountAndDateOfTheCollection() {
        Payment cash = cashPayment();

        assertThat(cash.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(cash.getGateway()).isEqualTo(PaymentGatewayCode.MANUAL);
        assertThat(cash.getMethodType()).isEqualTo(PaymentMethodType.CASH);
        assertThat(cash.getAmount()).isEqualTo(ars("80000.00"));
        assertThat(cash.getApprovedAmount()).isEqualTo(ars("80000.00"));
        assertThat(cash.getRefundedAmount()).isEqualTo(ars("0.00"));
        assertThat(cash.getPaidOn()).isEqualTo(PAID_ON);
        assertThat(cash.getRegisteredAt()).isNotNull().isEqualTo(cash.getStartedAt());
        assertThat(cash.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(cash.getIdempotencyKey()).isEqualTo("key-cash");
        assertThat(cash.getRegisteredByPersonId()).isEqualTo(ACTOR.personId());
    }

    @Test
    @Tag("D-082")
    @Tag("D-169")
    void aPendingTransferKeepsTheDeclaredAmountAndGetsTheVerifiedOneWhenApproved() {
        Payment transfer = pendingBankTransfer();

        assertThat(transfer.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(transfer.getApprovedAmount()).isNull();
        assertThat(transfer.getPaidOn()).isNull();

        transfer.approvePayment(ars("50000.00"), LocalDate.of(2027, 3, 14), StatusChangeActor.ofPerson(9L), "verificado por menos");

        assertThat(transfer.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(transfer.getAmount()).isEqualTo(ars("80000.00"));
        assertThat(transfer.getApprovedAmount()).isEqualTo(ars("50000.00"));
        assertThat(transfer.getPaidOn()).isEqualTo(LocalDate.of(2027, 3, 14));
        assertThat(transfer.getReviewedByPersonId()).isEqualTo(9L);
        assertThat(transfer.getReviewNote()).isEqualTo("verificado por menos");
    }

    @Test
    @Tag("D-082")
    void rejectingAndCancellingRecordTheReasonAndTheReviewer() {
        Payment rejected = pendingBankTransfer();
        rejected.rejectPayment("comprobante ilegible", StatusChangeActor.ofPerson(9L));
        Payment cancelled = pendingGatewayPayment(PaymentGatewayCode.MERCADO_PAGO);
        cancelled.cancelPendingPayment("el alumno abandonó el pago", StatusChangeActor.ofProcess("PAYMENT_EXPIRATION"));

        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(rejected.getRejectionReason()).isEqualTo("comprobante ilegible");
        assertThat(rejected.getReviewedByPersonId()).isEqualTo(9L);
        assertThat(cancelled.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.statusChanges().get(1).getActorProcess()).isEqualTo("PAYMENT_EXPIRATION");
        assertThat(cancelled.statusChanges().get(1).getReason()).isEqualTo("el alumno abandonó el pago");
    }

    @Test
    @Tag("D-082")
    void approvingOrRejectingOutOfOrderIsRefusedWithoutSideEffects() {
        Payment cash = cashPayment();
        Payment rejected = pendingBankTransfer();
        rejected.rejectPayment("no", ACTOR);

        assertThatThrownBy(() -> cash.approvePayment(ars("1.00"), PAID_ON, ACTOR, null)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThatThrownBy(() -> cash.rejectPayment("no", ACTOR)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThatThrownBy(() -> rejected.approvePayment(ars("1.00"), PAID_ON, ACTOR, null)).isInstanceOf(IllegalPaymentStatusTransitionException.class);

        assertThat(cash.getApprovedAmount()).isEqualTo(ars("80000.00"));
        assertThat(cash.getRejectionReason()).isNull();
        assertThat(rejected.getApprovedAmount()).isNull();
        assertThat(rejected.getPaidOn()).isNull();
    }

    @Test
    @Tag("D-097")
    @Tag("D-098")
    void thePaymentMustBeInTheCurrencyOfTheAccountAndGreaterThanZero() {
        StudentCurrentAccount arsAccount = arsAccount();
        NonNegativeMoney usd = NonNegativeMoney.of(new BigDecimal("100.00"), Currency.USD);
        Payment pending = pendingBankTransfer();

        assertThatThrownBy(() -> Payment.registerApprovedCashPayment(arsAccount, usd, PAID_ON, new CashPaymentDetails("R"), "k", ACTOR))
                .isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertThatThrownBy(() -> Payment.registerApprovedCashPayment(arsAccount, ars("0.00"), PAID_ON, new CashPaymentDetails("R"), "k", ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pending.approvePayment(usd, PAID_ON, ACTOR, null)).isInstanceOf(StudentCurrentAccountCurrencyMismatchException.class);
        assertThatThrownBy(() -> pending.approvePayment(ars("0.00"), PAID_ON, ACTOR, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(pending.getStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @Tag("D-082")
    void theMethodTypeComesFromTheDetailsOfTheMethod() {
        assertThat(new CashPaymentDetails("R").methodType()).isEqualTo(PaymentMethodType.CASH);
        assertThat(transferDetails().methodType()).isEqualTo(PaymentMethodType.BANK_TRANSFER);
        assertThat(new CardPaymentDetails(CardPaymentType.DEBIT, 1, "1234").methodType()).isEqualTo(PaymentMethodType.CARD);
        assertThat(new WalletPaymentDetails("MERCADO_PAGO_WALLET", "payer@example.com").methodType()).isEqualTo(PaymentMethodType.WALLET);
    }

    @Test
    @Tag("D-082")
    void aManualReviewCannotApproveOrRejectAGatewayPayment() {
        Payment card = pendingGatewayPayment(PaymentGatewayCode.MERCADO_PAGO);

        assertThatThrownBy(() -> card.approvePayment(ars("80000.00"), PAID_ON, ACTOR, null)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThatThrownBy(() -> card.rejectPayment("no", ACTOR)).isInstanceOf(IllegalPaymentStatusTransitionException.class);
        assertThat(card.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(card.statusChanges()).hasSize(1);
    }

    @Test
    @Tag("D-082")
    void aManualTransitionKeepsTheGatewayRawStatusTheGatewayLastReported() {
        Payment card = pendingGatewayPayment(PaymentGatewayCode.MERCADO_PAGO);

        card.cancelPendingPayment("el alumno desistio", ACTOR);

        assertThat(card.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(card.getGatewayRawStatus()).isEqualTo("pending");
        assertThat(card.getGatewayRawStatusDetail()).isEqualTo("pending_waiting_payment");
        assertThat(card.statusChanges().get(1).getGatewayRawStatus()).isNull();
    }
}
