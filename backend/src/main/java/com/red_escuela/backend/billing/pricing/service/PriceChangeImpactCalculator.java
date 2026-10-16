package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.MarketingDiscount;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.domain.PriceChangeImpact;
import com.red_escuela.backend.billing.pricing.domain.PriceVersion;
import com.red_escuela.backend.billing.pricing.exception.PriceNotFoundException;
import com.red_escuela.backend.billing.pricing.policy.ChangePriceCommand;
import com.red_escuela.backend.billing.pricing.policy.PriceChangeCheck;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.billing.pricing.service.MonthlyInstallmentPayerCounter;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;

@Component
public class PriceChangeImpactCalculator {
    private final PriceRepository priceRepository;
    private final MonthlyInstallmentPayerCounter payerCounter;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final Clock clock;

    public PriceChangeImpactCalculator(PriceRepository priceRepository, MonthlyInstallmentPayerCounter payerCounter, PriceVersionPolicyEvaluator policyEvaluator,
                                       Clock clock) {
        this.priceRepository = priceRepository;
        this.payerCounter = payerCounter;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PriceChangeImpact calculatePriceChangeImpact(Long priceId, NonNegativeMoney baseAmount, MarketingDiscount discount, LocalDate effectiveFrom) {
        Price price = priceRepository.findById(priceId).orElseThrow(() -> new PriceNotFoundException(priceId));
        policyEvaluator.requireNotRetroactiveStart(new PriceChangeCheck(effectiveFrom, LocalDate.now(clock), null));
        policyEvaluator.requireValidPriceVersion(new ChangePriceCommand(priceId, baseAmount, discount, effectiveFrom, null, null).toDraft(price.getCurrency()));
        NonNegativeMoney discountAmount = discount == null ? NonNegativeMoney.zero(price.getCurrency()) : discount.discountOn(baseAmount);
        NonNegativeMoney proposedFinalAmount = baseAmount.minusNotBelowZero(discountAmount);
        NonNegativeMoney currentFinalAmount = findBaselineVersion(price, effectiveFrom).map(PriceVersion::finalAmount).orElse(null);
        boolean recurring = price.getFeeType() == FeeType.MONTHLY_INSTALLMENT;
        long activeEnrollments = recurring ? countPayingEnrollments(price, effectiveFrom) : 0;
        return PriceChangeImpact.of(effectiveFrom, recurring, activeEnrollments, currentFinalAmount, proposedFinalAmount, discountAmount);
    }

    private Optional<PriceVersion> findBaselineVersion(Price price, LocalDate effectiveFrom) {
        LocalDate today = LocalDate.now(clock);
        return price.findVersionEffectiveOn(effectiveFrom.isAfter(today) ? effectiveFrom.minusDays(1) : today);
    }

    private long countPayingEnrollments(Price price, LocalDate effectiveFrom) {
        YearMonth month = YearMonth.from(effectiveFrom);
        return price.getProgramId() != null
                ? payerCounter.countProgramEnrollmentsPayingMonthlyInstallments(price.getProgramId(), month)
                : payerCounter.countCourseEnrollmentsPayingMonthlyInstallments(price.getCourseId(), month);
    }
}
