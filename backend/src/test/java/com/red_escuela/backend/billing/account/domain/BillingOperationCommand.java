package com.red_escuela.backend.billing.account.domain;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

import java.math.BigDecimal;
import java.util.List;

public record BillingOperationCommand(Kind kind, BigDecimal amount, BigDecimal secondAmount, int pick) {

    public enum Kind {
        ISSUE_INVOICE_DEBIT,
        ISSUE_DEBIT_NOTE_DEBIT,
        RECEIVE_PAYMENT_CREDIT,
        ISSUE_CREDIT_NOTE_CREDIT,
        REVERSE_PAYMENT_CREDIT,
        PAY_REFUND
    }

    public static Arbitrary<List<BillingOperationCommand>> sequences(int maxSize) {
        Arbitrary<BigDecimal> amounts = Arbitraries.bigDecimals().between(new BigDecimal("0.01"), new BigDecimal("300000.00")).ofScale(2);
        Arbitrary<BigDecimal> secondAmounts = Arbitraries.bigDecimals().between(BigDecimal.ZERO, new BigDecimal("300000.00")).ofScale(2);
        Arbitrary<BillingOperationCommand> command = Combinators.combine(Arbitraries.of(Kind.class), amounts, secondAmounts, Arbitraries.integers().between(0, 1000))
                .as(BillingOperationCommand::new);
        return command.list().ofMaxSize(maxSize);
    }
}
