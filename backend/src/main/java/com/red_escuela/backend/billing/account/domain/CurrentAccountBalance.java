package com.red_escuela.backend.billing.account.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
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

    public static CurrentAccountBalance fromSignedAmount(Money signedAmount) {
        Objects.requireNonNull(signedAmount, "signedAmount");
        Currency currency = signedAmount.currency();
        if (signedAmount.isZero()) {
            return zero(currency);
        }
        if (signedAmount.isNegative()) {
            return new CurrentAccountBalance(NonNegativeMoney.of(Money.zero(currency).minus(signedAmount)), CurrentAccountBalanceSide.OWES);
        }
        return new CurrentAccountBalance(NonNegativeMoney.of(signedAmount), CurrentAccountBalanceSide.IN_FAVOR);
    }

    public Money toSignedAmount() {
        return switch (side) {
            case OWES -> Money.zero(amount.value().currency()).minus(amount.value());
            case ZERO, IN_FAVOR -> amount.value();
        };
    }

    public CurrentAccountBalance applyEntry(CurrentAccountEntryDirection direction, NonNegativeMoney entryAmount) {
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(entryAmount, "entryAmount");
        Money signed = toSignedAmount();
        Money next = direction == CurrentAccountEntryDirection.CREDIT ? signed.plus(entryAmount.value()) : signed.minus(entryAmount.value());
        return fromSignedAmount(next);
    }
}
