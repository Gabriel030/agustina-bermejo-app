package com.red_escuela.backend.billing.invoice.service.legacy;

import com.red_escuela.backend.billing.invoice.domain.DiscountType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.domain.issuing.DiscountCandidateOrigin;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceDiscountCandidate;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.operation.InvoiceDiscountCandidateStrategy;
import com.red_escuela.backend.billing.invoice.policy.issuing.PromotionDiscountInstallmentCapPolicy;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromotionDiscountCandidateStrategy implements InvoiceDiscountCandidateStrategy {

    private static final int MAX_NAME_LENGTH = 200;

    private final PromotionRedemptionRepository redemptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final PromotionDiscountInstallmentCapPolicy capPolicy = new PromotionDiscountInstallmentCapPolicy();

    public PromotionDiscountCandidateStrategy(PromotionRedemptionRepository redemptionRepository, InvoiceRepository invoiceRepository) {
        this.redemptionRepository = redemptionRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public DiscountType discountType() {
        return DiscountType.PROMOTION;
    }

    @Override
    public List<InvoiceDiscountCandidate> findDiscountCandidatesForInvoice(InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        if (request.paymentPlanSelectionId() == null) {
            return List.of();
        }
        return redemptionRepository.findByPaymentPlanSelectionIdForUpdate(request.paymentPlanSelectionId())
                .map(redemption -> candidatesOf(redemption.getPromotion(), redemption.getPaymentPlanSelection().getPaymentPlanOption().getDescription(), context))
                .orElse(List.of());
    }

    private List<InvoiceDiscountCandidate> candidatesOf(Promotion promotion, String planDescription, InvoiceIssuingContext context) {
        InvoiceIssuingRequest request = context.request();
        return promotion.getBenefits().stream()
                .filter(benefit -> benefit.getFeeType() == request.feeType())
                .map(benefit -> new BenefitUsage(benefit, countAppliedDiscounts(benefit, request)))
                .filter(usage -> capPolicy.evaluateRule(new PromotionDiscountInstallmentCapPolicy.Usage(usage.benefit().getInvoiceScopeCount(), usage.alreadyApplied())).allowed())
                .map(usage -> candidateOf(promotion, planDescription, usage, context.facts().chargeAmount(), request))
                .filter(candidate -> !candidate.discountAmount().value().isZero())
                .toList();
    }

    private long countAppliedDiscounts(PromotionBenefit benefit, InvoiceIssuingRequest request) {
        return invoiceRepository.countPromotionDiscountsOfStudent(benefit.getId(), request.studentId());
    }

    private InvoiceDiscountCandidate candidateOf(Promotion promotion, String planDescription, BenefitUsage usage, NonNegativeMoney base, InvoiceIssuingRequest request) {
        String name = "Promoción: " + promotion.getDescription() + " (plan " + planDescription + ")";
        return InvoiceDiscountCandidate.ofLegacySource(DiscountCandidateOrigin.LEGACY_PROMOTION, DiscountType.PROMOTION, usage.benefit().getId(), usage.benefit().getId(),
                name.length() > MAX_NAME_LENGTH ? name.substring(0, MAX_NAME_LENGTH) : name, request.invoiceType(), base,
                DiscountAmounts.discountOf(usage.benefit().getValue(), base), (int) usage.alreadyApplied() + 1, usage.benefit().getInvoiceScopeCount(), null);
    }

    private record BenefitUsage(PromotionBenefit benefit, long alreadyApplied) {
    }
}
