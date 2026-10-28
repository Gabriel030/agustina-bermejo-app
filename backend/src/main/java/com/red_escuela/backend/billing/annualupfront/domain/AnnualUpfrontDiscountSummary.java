package com.red_escuela.backend.billing.annualupfront.domain;

import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import com.red_escuela.backend.shared.domain.Money;

public record AnnualUpfrontDiscountSummary(Long definitionId, String name, MonetaryAdjustment calculation, Money totalSaving) {
}
