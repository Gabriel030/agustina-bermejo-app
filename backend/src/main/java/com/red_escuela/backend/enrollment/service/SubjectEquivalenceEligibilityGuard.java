package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SubjectAlreadyApprovedException;
import com.red_escuela.backend.enrollment.exception.SubjectInProgressException;
import com.red_escuela.backend.enrollment.exception.SubjectRegularException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubjectEquivalenceEligibilityGuard {

    private static final List<EnrollmentStatus> BLOCKING_STATUSES = List.of(
            EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR, EnrollmentStatus.ENROLLED, EnrollmentStatus.IN_PROGRESS);

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;

    public SubjectEquivalenceEligibilityGuard(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
    }

    public void requireSubjectOpenForEquivalence(Long programEnrollmentId, Long subjectId) {
        List<EnrollmentStatus> blockingStatuses = subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(programEnrollmentId, subjectId, BLOCKING_STATUSES)
                .stream()
                .map(SubjectSectionEnrollment::getStatus)
                .toList();
        if (blockingStatuses.contains(EnrollmentStatus.APPROVED)) {
            throw new SubjectAlreadyApprovedException(programEnrollmentId, subjectId);
        }
        if (blockingStatuses.contains(EnrollmentStatus.REGULAR)) {
            throw new SubjectRegularException(programEnrollmentId, subjectId);
        }
        if (!blockingStatuses.isEmpty()) {
            throw new SubjectInProgressException(programEnrollmentId, subjectId);
        }
    }
}
