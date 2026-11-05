package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.LocalDateTime;
import java.util.List;

public record AnnualUpfrontQuote(List<AnnualUpfrontQuoteLine> lines, AnnualUpfrontDiscountSummary discount, Money total, LocalDateTime paymentDeadline) {

    public AnnualUpfrontQuote {
        lines = List.copyOf(lines);
    }
}
