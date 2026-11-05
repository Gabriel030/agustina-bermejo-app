package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.collections.domain.EnrollmentKind;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStandingResponse;
import com.red_escuela.backend.billing.collections.dto.EnrollmentPaymentStatusChangeResponse;
import com.red_escuela.backend.billing.collections.exception.EnrollmentPaymentStandingNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class EnrollmentPaymentStandingReader {

    private final EnrollmentPaymentStandingQuery standingQuery;

    public EnrollmentPaymentStandingReader(EnrollmentPaymentStandingQuery standingQuery) {
        this.standingQuery = standingQuery;
    }

    public EnrollmentPaymentStandingResponse readStanding(EnrollmentKind kind, Long enrollmentId) {
        return findStanding(kind, enrollmentId).map(EnrollmentPaymentStandingResponse::from).orElseThrow(EnrollmentPaymentStandingNotFoundException::new);
    }

    public List<EnrollmentPaymentStatusChangeResponse> readHistory(EnrollmentKind kind, Long enrollmentId) {
        if (findStanding(kind, enrollmentId).isEmpty()) {
            throw new EnrollmentPaymentStandingNotFoundException();
        }
        List<EnrollmentPaymentStatusChangeView> changes = kind == EnrollmentKind.PROGRAM
                ? standingQuery.listStatusChangesOfProgramEnrollment(enrollmentId)
                : standingQuery.listStatusChangesOfCourseEnrollment(enrollmentId);
        return changes.stream().map(EnrollmentPaymentStatusChangeResponse::from).toList();
    }

    private Optional<EnrollmentPaymentStandingView> findStanding(EnrollmentKind kind, Long enrollmentId) {
        return kind == EnrollmentKind.PROGRAM
                ? standingQuery.findStandingOfProgramEnrollment(enrollmentId)
                : standingQuery.findStandingOfCourseEnrollment(enrollmentId);
    }
}
