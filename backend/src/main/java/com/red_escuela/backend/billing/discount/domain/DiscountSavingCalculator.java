package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.util.Objects;
import java.util.Optional;

public final class DiscountSavingCalculator {

    private DiscountSavingCalculator() {
    }

    public static NonNegativeMoney calculateSavingOnFinalPrice(MonetaryAdjustment calculation, NonNegativeMoney finalLineAmount, Optional<NonNegativeMoney> capPerInvoice) {
        Objects.requireNonNull(calculation, "calculation");
        Objects.requireNonNull(finalLineAmount, "finalLineAmount");
        Objects.requireNonNull(capPerInvoice, "capPerInvoice");
        Money line = finalLineAmount.value();
        Money saving = calculation.applyTo(line);
        Money withinCap = capPerInvoice.map(NonNegativeMoney::value).filter(cap -> saving.isGreaterThan(cap)).orElse(saving);
        Money withinLine = withinCap.isGreaterThan(line) ? line : withinCap;
        return NonNegativeMoney.of(withinLine);
    }
}
