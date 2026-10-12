package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-144")
@Tag("D-145")
class StudentCurrentAccountTest {

    @Test
    void anOpenedAccountStartsWithZeroBalanceZeroCreditAndNoEntries() {
        StudentCurrentAccount account = StudentCurrentAccount.openStudentCurrentAccount(10L, Currency.USD);

        assertThat(account.getStudentId()).isEqualTo(10L);
        assertThat(account.getCurrency()).isEqualTo(Currency.USD);
        assertThat(account.getBalance()).isEqualTo(CurrentAccountBalance.zero(Currency.USD));
        assertThat(account.getBalance().side()).isEqualTo(CurrentAccountBalanceSide.ZERO);
        assertThat(account.getUnallocatedCreditAmount()).isEqualTo(NonNegativeMoney.zero(Currency.USD));
        assertThat(account.getLastEntrySequence()).isZero();
        assertThat(account.getOpenedAt()).isNotNull();
    }

    @Test
    void anAccountNeedsItsStudentAndCurrency() {
        assertThatThrownBy(() -> StudentCurrentAccount.openStudentCurrentAccount(null, Currency.ARS)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StudentCurrentAccount.openStudentCurrentAccount(1L, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void aBalanceIsZeroExactlyWhenItsAmountIsZero() {
        NonNegativeMoney fifty = NonNegativeMoney.of(new BigDecimal("50.00"), Currency.ARS);

        assertThat(new CurrentAccountBalance(fifty, CurrentAccountBalanceSide.OWES).amount()).isEqualTo(fifty);
        assertThat(new CurrentAccountBalance(fifty, CurrentAccountBalanceSide.IN_FAVOR).side()).isEqualTo(CurrentAccountBalanceSide.IN_FAVOR);
        assertThatThrownBy(() -> new CurrentAccountBalance(fifty, CurrentAccountBalanceSide.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrentAccountBalance(NonNegativeMoney.zero(Currency.ARS), CurrentAccountBalanceSide.OWES)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CurrentAccountBalance(null, CurrentAccountBalanceSide.ZERO)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new CurrentAccountBalance(fifty, null)).isInstanceOf(NullPointerException.class);
    }
}
