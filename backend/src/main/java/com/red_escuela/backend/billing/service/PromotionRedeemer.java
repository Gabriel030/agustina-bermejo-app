package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.billing.repository.PromotionRepository;
import com.red_escuela.backend.enrollment.entity.CourseEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PromotionRedeemer {
    private final PromotionRepository promotionRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;

    public PromotionRedeemer(PromotionRepository promotionRepository, PromotionRedemptionRepository promotionRedemptionRepository) {
        this.promotionRepository = promotionRepository;
        this.promotionRedemptionRepository = promotionRedemptionRepository;
    }

    @Transactional
    public void redeemIfEligible(CourseEnrollment courseEnrollment, PaymentPlanSelection selection) {
        promotionRepository.findFirstActiveByCourseAndDateInWindow(courseEnrollment.getCourse().getId(), courseEnrollment.getEnrollmentDate().toLocalDate())
                .ifPresent(promotion -> redeem(promotion, selection));
    }

    @Transactional
    public void redeemIfEligible(ProgramEnrollment programEnrollment, PaymentPlanSelection selection) {
        promotionRepository.findFirstActiveByProgramAndDateInWindow(programEnrollment.getProgram().getId(), programEnrollment.getEnrollmentDate().toLocalDate())
                .ifPresent(promotion -> redeem(promotion, selection));
    }

    private void redeem(Promotion promotion, PaymentPlanSelection selection) {
        PromotionRedemption redemption = new PromotionRedemption();
        redemption.setPromotion(promotion);
        redemption.setPaymentPlanSelection(selection);
        promotionRedemptionRepository.save(redemption);
    }
}
