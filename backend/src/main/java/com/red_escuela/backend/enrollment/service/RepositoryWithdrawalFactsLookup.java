package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawalFactsLookup;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RepositoryWithdrawalFactsLookup implements WithdrawalFactsLookup {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public RepositoryWithdrawalFactsLookup(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WithdrawnEnrollmentFacts> findVoluntaryWithdrawalOfProgramEnrollment(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .filter(enrollment -> enrollment.getStatus() == ProgramEnrollmentStatus.WITHDRAWN && enrollment.getWithdrawnByPersonId() != null
                        && enrollment.getWithdrawalEffectiveDate() != null && enrollment.getWithdrawalRegisteredOn() != null)
                .map(enrollment -> WithdrawnEnrollmentFacts.ofProgramEnrollment(enrollment.getStudent().getId(), programEnrollmentId,
                        enrollment.getWithdrawalEffectiveDate(), enrollment.getWithdrawalRegisteredOn(), WithdrawalCause.VOLUNTARY));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WithdrawnEnrollmentFacts> findVoluntaryWithdrawalOfCourseEnrollment(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.WITHDRAWN && enrollment.getWithdrawnByPersonId() != null
                        && enrollment.getWithdrawalEffectiveDate() != null && enrollment.getWithdrawalRegisteredOn() != null)
                .map(enrollment -> WithdrawnEnrollmentFacts.ofCourseEnrollment(enrollment.getStudent().getId(), courseEnrollmentId,
                        enrollment.getWithdrawalEffectiveDate(), enrollment.getWithdrawalRegisteredOn(), WithdrawalCause.VOLUNTARY));
    }
}
