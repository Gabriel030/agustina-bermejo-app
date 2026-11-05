package com.red_escuela.backend.billing.pricing.usecase;

import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicyViolation;

import java.time.LocalDate;
import java.util.List;

public interface ValidatePriceChangeUseCase {
    List<PolicyViolation> validatePriceChange(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount marketingDiscount, LocalDate effectiveFrom, String changeReason);
}
