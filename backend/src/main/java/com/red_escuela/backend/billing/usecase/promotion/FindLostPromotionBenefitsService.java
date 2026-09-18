package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@UseCase
public class FindLostPromotionBenefitsService implements FindLostPromotionBenefitsUseCase {
    private final PromotionRedemptionRepository promotionRedemptionRepository;
    private final InvoiceRepository invoiceRepository;

    public FindLostPromotionBenefitsService(PromotionRedemptionRepository promotionRedemptionRepository, InvoiceRepository invoiceRepository) {
        this.promotionRedemptionRepository = promotionRedemptionRepository;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LostPromotionBenefitResponse> find() {
        return promotionRedemptionRepository.findAll().stream()
                .filter(redemption -> redemption.getPaymentPlanSelection().getEffectiveTo() != null)
                .flatMap(redemption -> lostBenefitsOf(redemption).stream())
                .toList();
    }

    private List<LostPromotionBenefitResponse> lostBenefitsOf(PromotionRedemption redemption) {
        PaymentPlanSelection selection = redemption.getPaymentPlanSelection();
        String studentName = resolveStudentName(selection);
        return redemption.getPromotion().getBenefits().stream()
                .map(benefit -> toLostBenefit(redemption, selection, benefit, studentName))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<LostPromotionBenefitResponse> toLostBenefit(PromotionRedemption redemption, PaymentPlanSelection selection, PromotionBenefit benefit, String studentName) {
        if (benefit.getInvoiceScopeCount() == null) {
            return Optional.empty();
        }
        long used = invoiceRepository.countByPaymentPlanSelectionAndFeeTypeAndPromotionBenefit(selection.getId(), benefit.getFeeType(), benefit.getId());
        int remaining = (int) (benefit.getInvoiceScopeCount() - used);
        if (remaining <= 0) {
            return Optional.empty();
        }
        return Optional.of(new LostPromotionBenefitResponse(
                redemption.getId(), redemption.getPromotion().getDescription(), studentName, benefit.getFeeType().name(),
                (int) used, benefit.getInvoiceScopeCount(), remaining, selection.getEffectiveTo()));
    }

    private String resolveStudentName(PaymentPlanSelection selection) {
        Person person = selection.getCourseEnrollment() != null
                ? selection.getCourseEnrollment().getStudent().getPerson()
                : selection.getProgramEnrollment().getStudent().getPerson();
        return person.getName() + " " + person.getLastname();
    }
}
