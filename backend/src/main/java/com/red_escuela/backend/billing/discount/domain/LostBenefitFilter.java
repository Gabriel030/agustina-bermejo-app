package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import org.springframework.lang.Nullable;

import java.time.YearMonth;

public record LostBenefitFilter(@Nullable Long discountDefinitionId, @Nullable InvoiceType feeType, @Nullable YearMonth cutOffFrom, @Nullable YearMonth cutOffTo) {

    public static LostBenefitFilter unfiltered() {
        return new LostBenefitFilter(null, null, null, null);
    }

    public boolean admits(Long benefitDefinitionId, InvoiceType benefitFeeType, YearMonth benefitCutOff) {
        return (discountDefinitionId == null || discountDefinitionId.equals(benefitDefinitionId))
                && (feeType == null || feeType == benefitFeeType)
                && (cutOffFrom == null || !benefitCutOff.isBefore(cutOffFrom))
                && (cutOffTo == null || !benefitCutOff.isAfter(cutOffTo));
    }
}
