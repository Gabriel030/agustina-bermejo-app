package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.operation.MoneyText;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;

import java.math.BigDecimal;
import java.time.YearMonth;

final class DiscountAdjustmentText {

    private DiscountAdjustmentText() {
    }

    static String of(MonetaryAdjustment adjustment) {
        return switch (adjustment) {
            case MonetaryAdjustment.Nominal nominal -> MoneyText.of(nominal.amount());
            case MonetaryAdjustment.Proportional proportional -> pointsOf(proportional.rate().points()) + " %";
        };
    }

    static String periodOf(YearMonth period) {
        return String.format("%02d/%d", period.getMonthValue(), period.getYear());
    }

    private static String pointsOf(BigDecimal points) {
        return points.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
