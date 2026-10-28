package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.configuration.exception.NoEffectiveConfigurationException;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.Optional;

@Component
public class ConfiguredLectiveYearReader {
    private final EnrollmentLectiveYearReader lectiveYearReader;

    public ConfiguredLectiveYearReader(EnrollmentLectiveYearReader lectiveYearReader) {
        this.lectiveYearReader = lectiveYearReader;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Optional<LectiveYearSpan> findLectiveYearWhenConfigured(EnrollmentReference enrollment, BillingEnrollmentFacts facts, YearMonth period) {
        try {
            return lectiveYearReader.findLectiveYearCovering(enrollment, facts, period);
        } catch (NoEffectiveConfigurationException noConfiguration) {
            return Optional.empty();
        }
    }
}
