package com.red_escuela.backend.billing.usecase.promotion;

import com.red_escuela.backend.billing.dto.LostPromotionBenefitResponse;
import com.red_escuela.backend.billing.entity.PaymentPlanSelection;
import com.red_escuela.backend.billing.entity.PromotionBenefit;
import com.red_escuela.backend.billing.entity.PromotionRedemption;
import com.red_escuela.backend.billing.entity.FeeType;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.billing.repository.PromotionBenefitUsage;
import com.red_escuela.backend.billing.repository.PromotionRedemptionRepository;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
        List<PromotionRedemption> redemptions = promotionRedemptionRepository.findWithClosedPlanSelectionAndBenefits();
        if (redemptions.isEmpty()) {
            return List.of();
        }
        List<Long> selectionIds = redemptions.stream().map(redemption -> redemption.getPaymentPlanSelection().getId()).toList();
        Map<UsageKey, Long> usage = invoiceRepository.countPromotionBenefitUsageBySelections(selectionIds).stream()
                .collect(Collectors.toMap(
                        row -> new UsageKey(row.getPaymentPlanSelectionId(), row.getFeeType(), row.getPromotionBenefitId()),
                        PromotionBenefitUsage::getUsed));
        return redemptions.stream()
                .flatMap(redemption -> lostBenefitsOf(redemption, usage).stream())
                .toList();
    }

    private List<LostPromotionBenefitResponse> lostBenefitsOf(PromotionRedemption redemption, Map<UsageKey, Long> usage) {
        PaymentPlanSelection selection = redemption.getPaymentPlanSelection();
        String studentName = resolveStudentName(selection);
        return redemption.getPromotion().getBenefits().stream()
                .map(benefit -> toLostBenefit(redemption, selection, benefit, studentName, usage))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<LostPromotionBenefitResponse> toLostBenefit(PromotionRedemption redemption, PaymentPlanSelection selection, PromotionBenefit benefit, String studentName, Map<UsageKey, Long> usage) {
        if (benefit.getInvoiceScopeCount() == null) {
            return Optional.empty();
        }
        long used = usage.getOrDefault(new UsageKey(selection.getId(), benefit.getFeeType(), benefit.getId()), 0L);
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

    private record UsageKey(Long paymentPlanSelectionId, FeeType feeType, Long promotionBenefitId) {
    }
}
