package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

final class DiscountAmounts {

    private DiscountAmounts() {
    }

    static NonNegativeMoney discountOf(MonetaryAdjustment adjustment, NonNegativeMoney base) {
        Money discount = adjustment.applyTo(base.value());
        return base.value().isGreaterThan(discount) ? NonNegativeMoney.of(discount) : base;
    }
}
