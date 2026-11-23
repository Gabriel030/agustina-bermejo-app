package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import com.red_escuela.backend.enrollment.api.EnrollmentBillingStandingPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EnrollmentBillingStandingPortAdapter implements EnrollmentBillingStandingPort {

    private final EnrollmentPaymentStandingQuery standingQuery;

    public EnrollmentBillingStandingPortAdapter(EnrollmentPaymentStandingQuery standingQuery) {
        this.standingQuery = standingQuery;
    }

    @Override
    public boolean isBillingStoppedForProgramEnrollment(Long programEnrollmentId) {
        return standingQuery.findStandingOfProgramEnrollment(programEnrollmentId).map(EnrollmentPaymentStandingView::stopsBilling).orElse(false);
    }

    @Override
    public Set<Long> findSuspendedProgramEnrollmentIds(Collection<Long> programEnrollmentIds) {
        return suspendedIds(standingQuery.findStatusesOfProgramEnrollments(programEnrollmentIds));
    }

    @Override
    public Set<Long> findSuspendedCourseEnrollmentIds(Collection<Long> courseEnrollmentIds) {
        return suspendedIds(standingQuery.findStatusesOfCourseEnrollments(courseEnrollmentIds));
    }

    private Set<Long> suspendedIds(Map<Long, EnrollmentPaymentStatus> statuses) {
        return statuses.entrySet().stream()
                .filter(entry -> entry.getValue() == EnrollmentPaymentStatus.SUSPENDED)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }
}
