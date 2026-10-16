package com.red_escuela.backend.billing.discount.service;

import com.red_escuela.backend.billing.discount.domain.EnrollmentKind;
import com.red_escuela.backend.billing.discount.domain.EnrollmentReference;
import com.red_escuela.backend.billing.discount.domain.LectiveYearSpan;
import com.red_escuela.backend.billing.discount.repository.LiveEnrollmentFeeRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class EnrollmentFeeIssuanceReader {
    private final LiveEnrollmentFeeRepository repository;

    public EnrollmentFeeIssuanceReader(LiveEnrollmentFeeRepository repository) {
        this.repository = repository;
    }

    public boolean enrollmentFeeIssuedIn(EnrollmentReference enrollment, Optional<LectiveYearSpan> lectiveYear) {
        if (enrollment.kind() == EnrollmentKind.COURSE) {
            return repository.countLiveOfCourseEnrollment(enrollment.id()) > 0;
        }
        return lectiveYear.isPresent() && repository.findLivePeriodsOfProgramEnrollment(enrollment.id()).stream().anyMatch(lectiveYear.get()::contains);
    }
}
