package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionStatus;
import com.red_escuela.backend.billing.exception.PromotionAlreadyClosedException;
import com.red_escuela.backend.billing.exception.PromotionNotFoundException;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class ClosePromotionService implements ClosePromotionUseCase {

    private final PromotionRepository promotionRepository;
    private final CurrentPersonService currentPersonService;

    public ClosePromotionService(PromotionRepository promotionRepository, CurrentPersonService currentPersonService) {
        this.promotionRepository = promotionRepository;
        this.currentPersonService = currentPersonService;
    }

    @Override
    @Transactional
    public void close(Long id) {
        Promotion promotion = promotionRepository.findById(id).orElseThrow(() -> new PromotionNotFoundException(id));
        if (promotion.getStatus() == PromotionStatus.CLOSED) {
            throw new PromotionAlreadyClosedException(id);
        }
        promotion.close(currentPersonService.resolveCurrentPerson());
        promotionRepository.save(promotion);
    }
}
