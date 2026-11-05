package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.LostBenefitFilter;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitsSlice;
import org.springframework.lang.Nullable;

public interface FindLostCohortPromotionBenefitsUseCase {
    LostPromotionBenefitsSlice findLostBenefits(LostBenefitFilter filter, @Nullable Integer page, @Nullable Integer size);
}
