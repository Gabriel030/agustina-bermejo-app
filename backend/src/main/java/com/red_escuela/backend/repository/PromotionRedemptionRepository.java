package com.red_escuela.backend.repository;

import com.red_escuela.backend.entity.PromotionRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PromotionRedemptionRepository extends JpaRepository<PromotionRedemption, Long> {

    Optional<PromotionRedemption> findByPaymentPlanSelectionId(Long paymentPlanSelectionId);
}
