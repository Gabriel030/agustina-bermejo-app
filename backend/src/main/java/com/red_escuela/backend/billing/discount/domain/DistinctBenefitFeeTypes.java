package com.red_escuela.backend.billing.discount.domain;

import com.red_escuela.backend.billing.discount.exception.DuplicatePromotionBenefitFeeTypeException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DistinctBenefitFeeTypes(List<FeeType> feeTypes) {

    public DistinctBenefitFeeTypes {
        Set<FeeType> seen = new HashSet<>();
        feeTypes.forEach(feeType -> {
            if (!seen.add(feeType)) {
                throw new DuplicatePromotionBenefitFeeTypeException(feeType.name());
            }
        });
        feeTypes = List.copyOf(feeTypes);
    }
}
