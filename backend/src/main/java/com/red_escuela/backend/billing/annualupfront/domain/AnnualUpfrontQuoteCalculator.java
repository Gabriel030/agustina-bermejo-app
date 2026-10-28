package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AnnualUpfrontQuoteCalculator {

    private AnnualUpfrontQuoteCalculator() {
    }

    public static AnnualUpfrontQuote calculateQuote(List<YearMonth> periods, NonNegativeMoney pricePerMonth, Map<Integer, InvoiceDiscountCandidate> winnerByLine,
                                                    LocalDateTime paymentDeadline) {
        Money base = pricePerMonth.value();
        List<AnnualUpfrontQuoteLine> lines = new ArrayList<>();
        Money total = Money.zero(base.currency());
        Money totalSaving = Money.zero(base.currency());
        for (int index = 0; index < periods.size(); index++) {
            int lineNumber = index + 1;
            Money saving = winnerByLine.containsKey(lineNumber) ? winnerByLine.get(lineNumber).discountAmount().value() : Money.zero(base.currency());
            Money finalAmount = base.minus(saving);
            lines.add(new AnnualUpfrontQuoteLine(lineNumber, periods.get(index), base, saving, finalAmount));
            total = total.plus(finalAmount);
            totalSaving = totalSaving.plus(saving);
        }
        return new AnnualUpfrontQuote(lines, summaryOf(winnerByLine, totalSaving), total, paymentDeadline);
    }

    private static AnnualUpfrontDiscountSummary summaryOf(Map<Integer, InvoiceDiscountCandidate> winnerByLine, Money totalSaving) {
        if (winnerByLine.isEmpty()) {
            return null;
        }
        InvoiceDiscountCandidate first = winnerByLine.values().iterator().next();
        return new AnnualUpfrontDiscountSummary(first.sourceId(), first.discountName(), first.calculation(), totalSaving);
    }
}
