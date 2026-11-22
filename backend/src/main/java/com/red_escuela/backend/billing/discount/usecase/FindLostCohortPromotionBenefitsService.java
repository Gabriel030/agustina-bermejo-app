package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.domain.LostBenefitFilter;
import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitsSlice;
import com.red_escuela.backend.billing.discount.service.LostCohortPromotionBenefitsReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.lang.Nullable;

@UseCase
public class FindLostCohortPromotionBenefitsService implements FindLostCohortPromotionBenefitsUseCase {
    private final LostCohortPromotionBenefitsReader reader;

    public FindLostCohortPromotionBenefitsService(LostCohortPromotionBenefitsReader reader) {
        this.reader = reader;
    }

    @Override
    public LostPromotionBenefitsSlice findLostBenefits(LostBenefitFilter filter, @Nullable Integer page, @Nullable Integer size) {
        return reader.readLostBenefitsSlice(filter, page, size);
    }
}
