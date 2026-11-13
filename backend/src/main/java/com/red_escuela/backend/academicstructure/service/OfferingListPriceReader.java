package com.red_escuela.backend.academicstructure.service;

import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.pricing.OfferingFee;
import com.red_escuela.backend.shared.pricing.OfferingPriceResolver;
import com.red_escuela.backend.shared.pricing.OfferingReference;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class OfferingListPriceReader {
    private final OfferingPriceResolver offeringPriceResolver;

    public OfferingListPriceReader(OfferingPriceResolver offeringPriceResolver) {
        this.offeringPriceResolver = offeringPriceResolver;
    }

    public Optional<Money> findProgramTuition(Long programId) {
        return offeringPriceResolver.findCurrentListPrice(OfferingReference.ofProgram(programId), OfferingFee.MONTHLY_INSTALLMENT);
    }

    public Optional<Money> findProgramEnrollmentFee(Long programId) {
        return offeringPriceResolver.findCurrentListPrice(OfferingReference.ofProgram(programId), OfferingFee.ENROLLMENT_FEE);
    }

    public Optional<Money> findCourseTuition(Long courseId) {
        return offeringPriceResolver.findCurrentListPrice(OfferingReference.ofCourse(courseId), OfferingFee.MONTHLY_INSTALLMENT);
    }
}
