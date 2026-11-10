package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SubjectAlreadyApprovedException;
import com.red_escuela.backend.enrollment.exception.SubjectAlreadyRegularException;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SubjectRetakeGuard {

    private static final List<EnrollmentStatus> PASSED_STATUSES = List.of(EnrollmentStatus.APPROVED, EnrollmentStatus.REGULAR);

    private final SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository;

    public SubjectRetakeGuard(SubjectSectionEnrollmentRepository subjectSectionEnrollmentRepository) {
        this.subjectSectionEnrollmentRepository = subjectSectionEnrollmentRepository;
    }

    public void requireNotAlreadyPassed(Long programEnrollmentId, Long subjectId, Long ignoredEnrollmentId) {
        List<SubjectSectionEnrollment> passed = passedEnrollments(programEnrollmentId, subjectId, ignoredEnrollmentId);
        if (passed.stream().anyMatch(enrollment -> enrollment.getStatus() == EnrollmentStatus.APPROVED)) {
            throw new SubjectAlreadyApprovedException(programEnrollmentId, subjectId);
        }
        if (!passed.isEmpty()) {
            throw new SubjectAlreadyRegularException(programEnrollmentId, subjectId);
        }
    }

    public boolean isAlreadyPassed(Long programEnrollmentId, Long subjectId, Long ignoredEnrollmentId) {
        return !passedEnrollments(programEnrollmentId, subjectId, ignoredEnrollmentId).isEmpty();
    }

    private List<SubjectSectionEnrollment> passedEnrollments(Long programEnrollmentId, Long subjectId, Long ignoredEnrollmentId) {
        return subjectSectionEnrollmentRepository
                .findByProgramEnrollmentIdAndSubjectSection_Subject_IdAndStatusIn(
                        programEnrollmentId, subjectId, PASSED_STATUSES)
                .stream()
                .filter(enrollment -> !enrollment.getId().equals(ignoredEnrollmentId))
                .toList();
    }
}
