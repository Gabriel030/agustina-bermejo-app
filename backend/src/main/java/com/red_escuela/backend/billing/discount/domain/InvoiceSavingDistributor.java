package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class InvoiceSavingDistributor {

    private static final int CENTS = 2;

    private InvoiceSavingDistributor() {
    }

    public static List<NonNegativeMoney> distributeInvoiceSavingAcrossLines(MonetaryAdjustment calculation, Optional<NonNegativeMoney> capPerInvoice, List<NonNegativeMoney> lineBases) {
        if (lineBases.isEmpty()) {
            return List.of();
        }
        Currency currency = lineBases.get(0).value().currency();
        BigDecimal totalBase = lineBases.stream().map(base -> base.value().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> naturalByLine = calculation instanceof MonetaryAdjustment.Proportional
                ? lineBases.stream().map(base -> calculation.applyTo(base.value()).amount()).toList()
                : null;
        BigDecimal natural = naturalByLine != null ? naturalByLine.stream().reduce(BigDecimal.ZERO, BigDecimal::add) : calculation.applyTo(Money.of(totalBase, currency)).amount();
        BigDecimal total = natural.min(totalBase);
        if (capPerInvoice.isPresent()) {
            total = total.min(capPerInvoice.get().value().amount());
        }
        if (naturalByLine != null && total.compareTo(natural) == 0) {
            return naturalByLine.stream().map(amount -> NonNegativeMoney.of(amount, currency)).toList();
        }
        return allocateProportionallyToBase(total, lineBases, totalBase, currency);
    }

    private static List<NonNegativeMoney> allocateProportionallyToBase(BigDecimal total, List<NonNegativeMoney> lineBases, BigDecimal totalBase, Currency currency) {
        int last = lineBases.size() - 1;
        List<BigDecimal> shares = new ArrayList<>();
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < last; index++) {
            BigDecimal share = totalBase.signum() == 0 ? BigDecimal.ZERO
                    : total.multiply(lineBases.get(index).value().amount()).divide(totalBase, CENTS, RoundingMode.DOWN);
            shares.add(share);
            allocated = allocated.add(share);
        }
        shares.add(total.subtract(allocated));
        BigDecimal excess = shares.get(last).subtract(lineBases.get(last).value().amount()).max(BigDecimal.ZERO);
        shares.set(last, shares.get(last).subtract(excess));
        for (int index = last - 1; index >= 0 && excess.signum() > 0; index--) {
            BigDecimal room = lineBases.get(index).value().amount().subtract(shares.get(index));
            BigDecimal moved = excess.min(room);
            shares.set(index, shares.get(index).add(moved));
            excess = excess.subtract(moved);
        }
        return shares.stream().map(amount -> NonNegativeMoney.of(amount, currency)).toList();
    }
}
