package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeFreeze;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.enrollment.usecase.EnrollmentLifecycleQuery;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class LateFeeFreezeResolver {

    private final EnrollmentLifecycleQuery enrollmentLifecycle;

    public LateFeeFreezeResolver(EnrollmentLifecycleQuery enrollmentLifecycle) {
        this.enrollmentLifecycle = enrollmentLifecycle;
    }

    public LateFeeFreeze resolveFreezeOfEnrollment(Long programEnrollmentId, Long courseEnrollmentId, LatePaymentTerms dueDateTerms, LatePaymentTimeline timeline) {
        Optional<LocalDate> endDate = findEnrollmentEndDate(programEnrollmentId, courseEnrollmentId);
        boolean frozenByRuleOfTheEndDate = endDate.map(end -> timeline.termsOn(end, dueDateTerms).freezeLateFeesOnWithdrawal()).orElse(false);
        return new LateFeeFreeze(endDate, frozenByRuleOfTheEndDate);
    }

    private Optional<LocalDate> findEnrollmentEndDate(Long programEnrollmentId, Long courseEnrollmentId) {
        if (programEnrollmentId != null) {
            return enrollmentLifecycle.findProgramEnrollmentEndDate(programEnrollmentId);
        }
        if (courseEnrollmentId != null) {
            return enrollmentLifecycle.findCourseEnrollmentEndDate(courseEnrollmentId);
        }
        return Optional.empty();
    }
}
