package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class RepositoryBillingEnrollmentLookup implements BillingEnrollmentLookup {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public RepositoryBillingEnrollmentLookup(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingEnrollmentFacts> findProgramEnrollmentFacts(Long programEnrollmentId) {
        return programEnrollmentRepository.findById(programEnrollmentId)
                .map(enrollment -> new BillingEnrollmentFacts(enrollment.getStudent().getId(), enrollment.getProgram().getCurrency()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingEnrollmentFacts> findCourseEnrollmentFacts(Long courseEnrollmentId) {
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .map(enrollment -> new BillingEnrollmentFacts(enrollment.getStudent().getId(), enrollment.getCourse().getCurrency()));
    }
}
