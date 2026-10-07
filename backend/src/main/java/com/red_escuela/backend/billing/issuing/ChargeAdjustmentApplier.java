package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.issuing.policy.EntryFeeDiscountPolicy;
import com.red_escuela.backend.billing.issuing.policy.PromotionBenefitCapPolicy;
import com.red_escuela.backend.billing.issuing.policy.ScholarshipAppliesPolicy;
import com.red_escuela.backend.billing.issuing.policy.UpfrontDiscountPolicy;
import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceAdjustmentType;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.Promotion;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.billing.repository.ScholarshipRepository;
import com.red_escuela.backend.shared.domain.MonetaryAdjustment;
import org.springframework.stereotype.Component;

@Component
public class ChargeAdjustmentApplier {
    private final ScholarshipRepository scholarshipRepository;
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final InvoiceRepository invoiceRepository;
    private final EntryFeeDiscountPolicy entryFeeDiscountPolicy = new EntryFeeDiscountPolicy();
    private final UpfrontDiscountPolicy upfrontDiscountPolicy = new UpfrontDiscountPolicy();
    private final ScholarshipAppliesPolicy scholarshipAppliesPolicy = new ScholarshipAppliesPolicy();
    private final PromotionBenefitCapPolicy promotionBenefitCapPolicy = new PromotionBenefitCapPolicy();

    public ChargeAdjustmentApplier(ScholarshipRepository scholarshipRepository, PromotionRedemptionRepository promotionRedemptionRepository,
                                   InvoiceRepository invoiceRepository) {
        this.scholarshipRepository = scholarshipRepository;
        this.promotionRedemptionRepository = promotionRedemptionRepository;
        this.invoiceRepository = invoiceRepository;
    }

    public void apply(Invoice invoice, IssuanceContext context) {
        ChargeRequest request = context.request();
        applyScholarship(invoice, request.target());
        if (request.selection() != null) {
            applyPromotion(invoice, request.selection());
        }
        if (entryFeeDiscountPolicy.evaluateRule(context).allowed()) {
            var discount = context.entryFeeDiscount();
            invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, new MonetaryAdjustment.Proportional(discount),
                    "Matrícula de ingreso con descuento de " + discount.points().stripTrailingZeros().toPlainString() + "%", null);
        }
        if (upfrontDiscountPolicy.evaluateRule(context).allowed()) {
            invoice.applyAdjustment(InvoiceAdjustmentType.DISCOUNT, context.upfrontDiscount(), "Descuento por pago anual", null);
        }
        invoiceRepository.save(invoice);
    }

    private void applyScholarship(Invoice invoice, ChargeTarget target) {
        var scholarship = switch (target) {
            case ChargeTarget.ProgramStudent t -> scholarshipRepository.findActiveByProgramEnrollmentIdForUpdate(t.enrollment().getId());
            case ChargeTarget.CourseStudent t -> scholarshipRepository.findActiveByCourseEnrollmentIdForUpdate(t.enrollment().getId());
            default -> java.util.Optional.<com.red_escuela.backend.billing.entity.Scholarship>empty();
        };
        scholarship.ifPresent(s -> {
            boolean programCharge = target instanceof ChargeTarget.ProgramStudent;
            if (!scholarshipAppliesPolicy.evaluateRule(new ScholarshipAppliesPolicy.Candidate(s, invoice.getFeeType(), programCharge)).allowed()) {
                return;
            }
            s.consumeOne(invoice);
            invoice.applyAdjustment(InvoiceAdjustmentType.SCHOLARSHIP, s.getValue(), s.getReason(), null);
            scholarshipRepository.save(s);
        });
    }

    private void applyPromotion(Invoice invoice, PaymentPlanSelection selection) {
        promotionRedemptionRepository.findByPaymentPlanSelectionIdForUpdate(selection.getId()).ifPresent(redemption -> {
            Promotion promotion = redemption.getPromotion();
            for (PromotionBenefit benefit : promotion.getBenefits()) {
                if (benefit.getFeeType() != invoice.getFeeType()) {
                    continue;
                }
                long alreadyApplied = invoiceRepository.countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit(
                        selection.getId(), invoice.getFeeType(), benefit.getId());
                if (promotionBenefitCapPolicy.evaluateRule(new PromotionBenefitCapPolicy.Usage(benefit.getInvoiceScopeCount(), alreadyApplied)).allowed()) {
                    invoice.applyAdjustment(InvoiceAdjustmentType.PROMOTION, benefit.getValue(), "Promoción: " + promotion.getDescription(), null, benefit);
                }
            }
        });
    }
}
