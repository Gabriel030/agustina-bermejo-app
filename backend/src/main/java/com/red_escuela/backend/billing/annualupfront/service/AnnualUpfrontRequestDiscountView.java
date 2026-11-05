package com.red_escuela.backend.billing.annualupfront.service;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

public record AnnualUpfrontRequestDiscountView(String name, MonetaryAdjustment calculation, Money totalSaving) {
}
