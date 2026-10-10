package com.red_escuela.backend.billing.pricing.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.pricing.domain.Price;
import com.red_escuela.backend.billing.pricing.policy.PriceVersionDraft;
import com.red_escuela.backend.billing.pricing.repository.PriceRepository;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceCatalog;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

@Service
public class OfferingPriceCatalogService implements OfferingPriceCatalog {
    private final PriceRepository priceRepository;
    private final PriceVersionPolicyEvaluator policyEvaluator;
    private final Clock clock;

    public OfferingPriceCatalogService(PriceRepository priceRepository, PriceVersionPolicyEvaluator policyEvaluator, Clock clock) {
        this.priceRepository = priceRepository;
        this.policyEvaluator = policyEvaluator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void registerInitialOfferingPrice(OfferingReference offering, OfferingFee fee, Money amount, Long actorPersonId) {
        FeeType feeType = FeeType.valueOf(fee.name());
        Price price = findOrCreatePrice(offering, feeType, amount);
        publishPriceVersion(price, amount, actorPersonId, initialReason(fee), true);
    }

    @Override
    @Transactional
    public void adjustOfferingPrice(OfferingReference offering, OfferingFee fee, Money amount, Long actorPersonId) {
        FeeType feeType = FeeType.valueOf(fee.name());
        Price price = findOrCreatePrice(offering, feeType, amount);
        publishPriceVersion(price, amount, actorPersonId, adjustmentReason(fee), false);
    }

    private void publishPriceVersion(Price price, Money amount, Long actorPersonId, String changeReason, boolean initialVersion) {
        policyEvaluator.requireValidPriceVersion(PriceVersionDraft.withoutDiscount(price.getCurrency(), amount.currency(), amount.amount()));
        price.retireEffectiveVersions(Instant.now(clock));
        priceRepository.saveAndFlush(price);
        price.publishPriceVersion(NonNegativeMoney.of(amount), null, LocalDate.now(clock), changeReason, actorPersonId, initialVersion,
                LocalDate.now(clock), Instant.now(clock));
        priceRepository.save(price);
    }

    private Price findOrCreatePrice(OfferingReference offering, FeeType feeType, Money amount) {
        return (offering.belongsToProgram()
                ? priceRepository.findByProgramIdAndFeeType(offering.programId(), feeType)
                : priceRepository.findByCourseIdAndFeeType(offering.courseId(), feeType))
                .orElseGet(() -> offering.belongsToProgram()
                        ? Price.forProgram(offering.programId(), feeType, amount.currency(), descriptionOf(feeType))
                        : Price.forCourse(offering.courseId(), feeType, amount.currency(), descriptionOf(feeType)));
    }

    private static String descriptionOf(FeeType feeType) {
        return feeType == FeeType.ENROLLMENT_FEE ? "Matrícula" : "Cuota mensual";
    }

    private static String initialReason(OfferingFee fee) {
        return fee == OfferingFee.ENROLLMENT_FEE ? "Matrícula inicial" : "Precio inicial";
    }

    private static String adjustmentReason(OfferingFee fee) {
        return fee == OfferingFee.ENROLLMENT_FEE ? "Ajuste de matrícula" : "Ajuste de precio";
    }
}
