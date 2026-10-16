package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.enrollment.api.ActiveEnrollmentCounter;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;

@Component
public class PriceChangeImpactCalculator {
    private final PriceRepository priceRepository;
    private final ActiveEnrollmentCounter activeEnrollmentCounter;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final Clock clock;

    public PriceChangeImpactCalculator(PriceRepository priceRepository, ActiveEnrollmentCounter activeEnrollmentCounter, PriceVersionPolicyEvaluator policyEvaluator,
                                       Clock clock) {
        this.priceRepository = priceRepository;
        this.activeEnrollmentCounter = activeEnrollmentCounter;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PriceChangeImpact calculatePriceChangeImpact(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom) {
        Price price = priceRepository.findById(priceId).orElseThrow(() -> new PriceNotFoundException(priceId));
        policyEvaluator.requireValidPriceVersion(new ChangePriceCommand(priceId, baseAmount, discount, effectiveFrom, null, null).toDraft(price.getCurrency()));
        NonNegativeMoney discountAmount = discount == null ? NonNegativeMoney.zero(price.getCurrency()) : discount.discountOn(baseAmount);
        NonNegativeMoney proposedFinalAmount = baseAmount.minusNotBelowZero(discountAmount);
        NonNegativeMoney currentFinalAmount = findBaselineVersion(price, effectiveFrom).map(PriceVersion::finalAmount).orElse(null);
        boolean recurring = price.getFeeType() == FeeType.MONTHLY_INSTALLMENT;
        long activeEnrollments = recurring ? countActiveEnrollments(price) : 0;
        return PriceChangeImpact.of(effectiveFrom, recurring, activeEnrollments, currentFinalAmount, proposedFinalAmount, discountAmount);
    }

    private Optional<PriceVersion> findBaselineVersion(Price price, LocalDate effectiveFrom) {
        LocalDate today = LocalDate.now(clock);
        return price.findVersionEffectiveOn(effectiveFrom.isAfter(today) ? effectiveFrom.minusDays(1) : today);
    }

    private long countActiveEnrollments(Price price) {
        return price.getProgramId() != null
                ? activeEnrollmentCounter.countActiveProgramEnrollments(price.getProgramId())
                : activeEnrollmentCounter.countActiveCourseEnrollments(price.getCourseId());
    }
}
