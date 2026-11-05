package com.red_escuela.backend.billing.operation;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;

import java.text.NumberFormat;
import java.util.Locale;

public final class MoneyText {
    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");

    private MoneyText() {
    }

    public static String of(Money money) {
        NumberFormat format = NumberFormat.getNumberInstance(ARGENTINA);
        format.setMinimumFractionDigits(money.amount().stripTrailingZeros().scale() > 0 ? 2 : 0);
        format.setMaximumFractionDigits(2);
        String prefix = money.currency() == Currency.USD ? "US$" : "$";
        return prefix + format.format(money.amount());
    }
}
