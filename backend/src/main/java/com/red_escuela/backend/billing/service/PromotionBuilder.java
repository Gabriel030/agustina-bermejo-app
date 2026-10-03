package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.dto.CreatePromotionBenefitRequest;
import com.red_escuela.backend.billing.dto.CreatePromotionRequest;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.exception.DuplicatePromotionBenefitFeeTypeException;
import com.red_escuela.backend.enrollment.exception.InvalidWindowConfigurationException;
import com.red_escuela.backend.identity.service.CurrentPersonService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

@Service
public class PromotionBuilder {

    private final CurrentPersonService currentPersonService;

    public PromotionBuilder(CurrentPersonService currentPersonService) {
        this.currentPersonService = currentPersonService;
    }

    public Promotion build(CreatePromotionRequest request) {
        if (request.eligibilityValidFrom().isAfter(request.eligibilityValidTo())) {
            throw new InvalidWindowConfigurationException("eligibilityValidFrom no puede ser posterior a eligibilityValidTo");
        }
        Promotion promotion = new Promotion();
        promotion.setDescription(request.description());
        promotion.setEligibilityValidFrom(request.eligibilityValidFrom());
        promotion.setEligibilityValidTo(request.eligibilityValidTo());
        promotion.setCreatedBy(currentPersonService.resolveCurrentPerson());
        Set<FeeType> feeTypesYaUsados = new HashSet<>();
        for (CreatePromotionBenefitRequest benefitRequest : request.benefits()) {
            if (!feeTypesYaUsados.add(benefitRequest.feeType())) {
                throw new DuplicatePromotionBenefitFeeTypeException(benefitRequest.feeType().name());
            }
            PromotionBenefit benefit = new PromotionBenefit();
            benefit.setPromotion(promotion);
            benefit.setFeeType(benefitRequest.feeType());
            benefit.setValue(benefitRequest.value().toAdjustment());
            benefit.setInvoiceScopeCount(benefitRequest.invoiceScopeCount());
            promotion.getBenefits().add(benefit);
        }
        return promotion;
    }
}
