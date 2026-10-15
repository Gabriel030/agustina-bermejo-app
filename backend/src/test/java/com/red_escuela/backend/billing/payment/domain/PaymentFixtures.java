package com.red_escuela.backend.billing.payment.domain;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class PaymentFixtures {

    public static final StatusChangeActor ACTOR = StatusChangeActor.ofPerson(7L);
    public static final LocalDate PAID_ON = LocalDate.of(2027, 3, 15);

    private PaymentFixtures() {
    }

    public static NonNegativeMoney ars(String amount) {
        return NonNegativeMoney.of(new BigDecimal(amount), Currency.ARS);
    }

    public static StudentCurrentAccount arsAccount() {
        return StudentCurrentAccount.openStudentCurrentAccount(10L, Currency.ARS);
    }

    public static Payment cashPayment() {
        return Payment.registerApprovedCashPayment(arsAccount(), ars("80000.00"), PAID_ON, new CashPaymentDetails("R-1"), "key-cash", ACTOR);
    }

    public static BankTransferPaymentDetails transferDetails() {
        return new BankTransferPaymentDetails("Banco Nación", "0110000000000000000001", "OP-9", "Ana Pérez", PAID_ON);
    }

    public static Payment pendingBankTransfer() {
        return Payment.createPendingBankTransferPayment(arsAccount(), ars("80000.00"), transferDetails(), "key-transfer", ACTOR);
    }

    public static Payment pendingGatewayPayment(PaymentGatewayCode gateway) {
        return Payment.createPendingGatewayPayment(arsAccount(), ars("80000.00"), gateway, new CardPaymentDetails(CardPaymentType.CREDIT, 3, "4242"),
                "mp-1", new GatewayRawStatus("pending", "pending_waiting_payment"), "key-card", ACTOR);
    }
}
