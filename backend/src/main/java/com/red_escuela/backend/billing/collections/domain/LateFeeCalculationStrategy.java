package com.red_escuela.backend.billing.collections.domain;

import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.Percentage;

public interface LateFeeCalculationStrategy {

    NonNegativeMoney calculateLateFee(NonNegativeMoney finalLinePrice, Percentage dailyRate, int chargedDays);
}
