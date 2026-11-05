package com.red_escuela.backend.billing.discount.usecase;

import com.red_escuela.backend.billing.discount.service.PromotionContractCloser;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.security.access.prepost.PreAuthorize;

@UseCase
public class ClosePromotionByLegacyContractService implements ClosePromotionByLegacyContractUseCase {
    private final PromotionContractCloser closer;

    public ClosePromotionByLegacyContractService(PromotionContractCloser closer) {
        this.closer = closer;
    }

    @Override
    @PreAuthorize("hasRole('DIRECTOR')")
    public void closePromotion(Long promotionId) {
        closer.closePromotionAndItsSiblings(promotionId);
    }
}
