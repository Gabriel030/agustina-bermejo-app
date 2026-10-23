package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.billing.discount.service.LostCohortPromotionBenefitsReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class FindLostCohortPromotionBenefitsService implements FindLostCohortPromotionBenefitsUseCase {
    private final LostCohortPromotionBenefitsReader reader;

    public FindLostCohortPromotionBenefitsService(LostCohortPromotionBenefitsReader reader) {
        this.reader = reader;
    }

    @Override
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMINISTRATIVO', 'DIRECTOR')")
    public List<LostPromotionBenefitResponse> findLostBenefits() {
        return reader.readLostBenefits();
    }
}
