package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.red_escuela.backend.billing.account.domain.AccountTestData.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
class CurrentAccountBalanceTest {

    private static Money signed(String amount) {
        return Money.of(new BigDecimal(amount), Currency.ARS);
    }

    @Test
    void aNegativeSignedAmountIsWhatTheStudentOwes() {
        CurrentAccountBalance balance = CurrentAccountBalance.fromSignedAmount(signed("-550000.00"));

        assertThat(balance.amount()).isEqualTo(ars("550000.00"));
        assertThat(balance.side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(balance.toSignedAmount()).isEqualTo(signed("-550000.00"));
    }

    @Test
    void zeroIsTheZeroSide() {
        CurrentAccountBalance balance = CurrentAccountBalance.fromSignedAmount(signed("0.00"));

        assertThat(balance).isEqualTo(CurrentAccountBalance.zero(Currency.ARS));
        assertThat(balance.toSignedAmount()).isEqualTo(signed("0.00"));
    }

    @Test
    void aPositiveSignedAmountIsInFavor() {
        CurrentAccountBalance balance = CurrentAccountBalance.fromSignedAmount(signed("100000.00"));

        assertThat(balance.amount()).isEqualTo(ars("100000.00"));
        assertThat(balance.side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
        assertThat(balance.toSignedAmount()).isEqualTo(signed("100000.00"));
    }

    @Test
    void aDebitCrossesFromInFavorToOwes() {
        CurrentAccountBalance inFavor = CurrentAccountBalance.fromSignedAmount(signed("100.00"));

        CurrentAccountBalance next = inFavor.applyEntry(CurrentAccountEntryDirection.DEBIT, ars("250.00"));

        assertThat(next.side()).isEqualTo(CurrentAccountBalanceSide.OWES);
        assertThat(next.amount()).isEqualTo(ars("150.00"));
    }

    @Test
    void aCreditCrossesFromOwesToInFavor() {
        CurrentAccountBalance owes = CurrentAccountBalance.fromSignedAmount(signed("-100.00"));

        CurrentAccountBalance next = owes.applyEntry(CurrentAccountEntryDirection.CREDIT, ars("250.00"));

        assertThat(next.side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
        assertThat(next.amount()).isEqualTo(ars("150.00"));
    }

    @Test
    void aCreditEqualToTheDebtLeavesTheBalanceAtZero() {
        CurrentAccountBalance owes = CurrentAccountBalance.fromSignedAmount(signed("-100.00"));

        assertThat(owes.applyEntry(CurrentAccountEntryDirection.CREDIT, ars("100.00"))).isEqualTo(CurrentAccountBalance.zero(Currency.ARS));
    }

    @Test
    void aDebitAddsToWhatIsAlreadyOwedAndACreditAddsToWhatIsInFavor() {
        assertThat(CurrentAccountBalance.zero(Currency.ARS).applyEntry(CurrentAccountEntryDirection.DEBIT, ars("30.00")).toSignedAmount())
                .isEqualTo(signed("-30.00"));
        assertThat(CurrentAccountBalance.zero(Currency.ARS).applyEntry(CurrentAccountEntryDirection.CREDIT, ars("30.00")).toSignedAmount())
                .isEqualTo(signed("30.00"));
    }

    @Test
    void anEntryInAnotherCurrencyIsRejected() {
        assertThatThrownBy(() -> CurrentAccountBalance.zero(Currency.ARS).applyEntry(CurrentAccountEntryDirection.DEBIT, AccountTestData.usd("1.00")))
                .isInstanceOf(com.red_escuela.backend.shared.domain.CurrencyMismatchException.class);
    }

    @Test
    void aZeroSideWithAnAmountIsRejected() {
        assertThatThrownBy(() -> new CurrentAccountBalance(ars("1.00"), CurrentAccountBalanceSide.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrentAccountBalance(ars("0.00"), CurrentAccountBalanceSide.OWES)).isInstanceOf(IllegalArgumentException.class);
    }
}
