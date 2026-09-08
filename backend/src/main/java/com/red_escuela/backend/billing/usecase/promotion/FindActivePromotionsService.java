package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionStatus;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class FindActivePromotionsService implements FindActivePromotionsUseCase {

    private final PromotionRepository promotionRepository;

    public FindActivePromotionsService(PromotionRepository promotionRepository) {
        this.promotionRepository = promotionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Promotion> findActive() {
        return promotionRepository.findByStatus(PromotionStatus.ACTIVE);
    }
}
