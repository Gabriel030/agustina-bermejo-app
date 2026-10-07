package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

@Embeddable
public record CurrentAccountBalance(
        @Embedded
        @AttributeOverrides({
                @AttributeOverride(name = "value.amount", column = @Column(name = "balance_amount", nullable = false)),
                @AttributeOverride(name = "value.currency", column = @Column(name = "balance_amount_currency", nullable = false))})
        NonNegativeMoney amount,
        @Enumerated(EnumType.STRING)
        @Column(name = "balance_side", nullable = false, length = 10)
        CurrentAccountBalanceSide side) {

    public CurrentAccountBalance {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(side, "side");
        if ((side == CurrentAccountBalanceSide.ZERO) != amount.value().isZero()) {
            throw new IllegalArgumentException("A balance is ZERO exactly when its amount is zero");
        }
    }

    public static CurrentAccountBalance zero(Currency currency) {
        return new CurrentAccountBalance(NonNegativeMoney.zero(currency), CurrentAccountBalanceSide.ZERO);
    }
}
