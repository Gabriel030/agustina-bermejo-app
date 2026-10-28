package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStatusChangeView;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStanding;
import com.red_escuela.backend.billing.collections.domain.EnrollmentPaymentStatusChange;
import com.red_escuela.backend.billing.collections.repository.EnrollmentPaymentStandingRepository;
import com.red_escuela.backend.billing.entity.EnrollmentPaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class StoredEnrollmentPaymentStandingQuery implements EnrollmentPaymentStandingQuery {

    private final EnrollmentPaymentStandingRepository standingRepository;

    public StoredEnrollmentPaymentStandingQuery(EnrollmentPaymentStandingRepository standingRepository) {
        this.standingRepository = standingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentPaymentStandingView> findStandingOfProgramEnrollment(Long programEnrollmentId) {
        return standingRepository.findByProgramEnrollment(programEnrollmentId).map(StoredEnrollmentPaymentStandingQuery::viewOf);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EnrollmentPaymentStandingView> findStandingOfCourseEnrollment(Long courseEnrollmentId) {
        return standingRepository.findByCourseEnrollment(courseEnrollmentId).map(StoredEnrollmentPaymentStandingQuery::viewOf);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentPaymentStatusChangeView> listStatusChangesOfProgramEnrollment(Long programEnrollmentId) {
        return standingRepository.findByProgramEnrollment(programEnrollmentId).map(StoredEnrollmentPaymentStandingQuery::changesOf).orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public List<EnrollmentPaymentStatusChangeView> listStatusChangesOfCourseEnrollment(Long courseEnrollmentId) {
        return standingRepository.findByCourseEnrollment(courseEnrollmentId).map(StoredEnrollmentPaymentStandingQuery::changesOf).orElse(List.of());
    }

    private static EnrollmentPaymentStandingView viewOf(EnrollmentPaymentStanding standing) {
        return new EnrollmentPaymentStandingView(standing.enrollmentId(), standing.getStudentId(), standing.getStatus(), standing.getSuspendedSinceDate());
    }

    private static List<EnrollmentPaymentStatusChangeView> changesOf(EnrollmentPaymentStanding standing) {
        return standing.statusChanges().stream().map(StoredEnrollmentPaymentStandingQuery::viewOf).toList();
    }

    private static EnrollmentPaymentStatusChangeView viewOf(EnrollmentPaymentStatusChange change) {
        return new EnrollmentPaymentStatusChangeView(change.getEvent(),
                change.getFromStatus() == null ? null : EnrollmentPaymentStatus.valueOf(change.getFromStatus()),
                EnrollmentPaymentStatus.valueOf(change.getToStatus()), change.getReason(), change.getActorPersonId(), change.getActorProcess(),
                change.getBusinessDate(), change.getOccurredAt());
    }
}
