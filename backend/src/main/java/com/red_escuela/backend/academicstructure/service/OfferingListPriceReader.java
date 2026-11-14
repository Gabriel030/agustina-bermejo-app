package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceResolver;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OfferingListPriceReader {
    private final OfferingPriceResolver offeringPriceResolver;

    public OfferingListPriceReader(OfferingPriceResolver offeringPriceResolver) {
        this.offeringPriceResolver = offeringPriceResolver;
    }

    public Optional<Money> findProgramTuition(Long programId) {
        return positive(offeringPriceResolver.findCurrentListPrice(OfferingReference.ofProgram(programId), OfferingFee.MONTHLY_INSTALLMENT));
    }

    public Optional<Money> findProgramEnrollmentFee(Long programId) {
        return positive(offeringPriceResolver.findCurrentListPrice(OfferingReference.ofProgram(programId), OfferingFee.ENROLLMENT_FEE));
    }

    public Optional<Money> findCourseTuition(Long courseId) {
        return positive(offeringPriceResolver.findCurrentListPrice(OfferingReference.ofCourse(courseId), OfferingFee.MONTHLY_INSTALLMENT));
    }

    private static Optional<Money> positive(Optional<Money> price) {
        return price.filter(amount -> amount.amount().signum() > 0);
    }

    public Map<Long, Money> findProgramTuitions(Collection<Long> programIds) {
        return positive(offeringPriceResolver.findCurrentListPrices(programIds, true, OfferingFee.MONTHLY_INSTALLMENT));
    }

    public Map<Long, Money> findProgramEnrollmentFees(Collection<Long> programIds) {
        return positive(offeringPriceResolver.findCurrentListPrices(programIds, true, OfferingFee.ENROLLMENT_FEE));
    }

    public Map<Long, Money> findCourseTuitions(Collection<Long> courseIds) {
        return positive(offeringPriceResolver.findCurrentListPrices(courseIds, false, OfferingFee.MONTHLY_INSTALLMENT));
    }

    private static Map<Long, Money> positive(Map<Long, Money> prices) {
        Map<Long, Money> positives = new HashMap<>();
        prices.forEach((offeringId, price) -> {
            if (price.amount().signum() > 0) {
                positives.put(offeringId, price);
            }
        });
        return positives;
    }
}
