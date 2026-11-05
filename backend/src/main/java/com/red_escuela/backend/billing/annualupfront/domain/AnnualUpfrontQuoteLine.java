package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.shared.domain.Money;

import java.time.YearMonth;

public record AnnualUpfrontQuoteLine(int lineNumber, YearMonth period, Money baseAmount, Money discountAmount, Money finalAmount) {
}
