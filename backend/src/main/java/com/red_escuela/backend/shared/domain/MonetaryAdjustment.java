package com.red_escuela.backend.shared.domain;

import java.util.Objects;

public sealed interface MonetaryAdjustment permits MonetaryAdjustment.Nominal, MonetaryAdjustment.Proportional {

    Money applyTo(Money base);

    default void requireCurrency(Currency currency) {
    }

    record Nominal(Money amount) implements MonetaryAdjustment {

        public Nominal {
            Objects.requireNonNull(amount, "amount");
            if (amount.isNegative()) {
                throw new NegativeAdjustmentAmountException();
            }
        }

        @Override
        public void requireCurrency(Currency currency) {
            if (amount.currency() != currency) {
                throw new CurrencyMismatchException(currency, amount.currency());
            }
        }

        @Override
        public Money applyTo(Money base) {
            Objects.requireNonNull(base, "base");
            if (base.currency() != amount.currency()) {
                throw new CurrencyMismatchException(base.currency(), amount.currency());
            }
            return amount;
        }
    }

    record Proportional(Percentage rate) implements MonetaryAdjustment {

        public Proportional {
            Objects.requireNonNull(rate, "rate");
        }

        @Override
        public Money applyTo(Money base) {
            Objects.requireNonNull(base, "base");
            return base.times(rate);
        }
    }
}
