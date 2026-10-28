package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.api.BillingEnrollmentFacts;
import com.red_escuela.backend.enrollment.api.BillingEnrollmentLookup;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
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
        Optional<ProgramEnrollmentRepository.BillingTemplateRef> ref = programEnrollmentRepository.findBillingTemplateRef(programEnrollmentId);
        return programEnrollmentRepository.findById(programEnrollmentId)
                .map(enrollment -> new BillingEnrollmentFacts(enrollment.getStudent().getId(), enrollment.getProgram().getCurrency(), enrollment.getProgram().getId(),
                        ref.map(ProgramEnrollmentRepository.BillingTemplateRef::getTemplateId).orElse(null), null, null, programEnrollmentRepository.isEnrolledAndNotSuspendedForNonPayment(programEnrollmentId)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BillingEnrollmentFacts> findCourseEnrollmentFacts(Long courseEnrollmentId) {
        Optional<CourseEnrollmentRepository.CourseTermDates> dates = courseEnrollmentRepository.findCourseTermDates(courseEnrollmentId);
        return courseEnrollmentRepository.findById(courseEnrollmentId)
                .map(enrollment -> new BillingEnrollmentFacts(enrollment.getStudent().getId(), enrollment.getCourse().getCurrency(), null, null,
                        dates.map(CourseEnrollmentRepository.CourseTermDates::getStartDate).map(this::termMonth).orElse(null),
                        dates.map(CourseEnrollmentRepository.CourseTermDates::getEndDate).map(this::termMonth).orElse(null)));
    }

    private YearMonth termMonth(LocalDateTime date) {
        return YearMonth.from(date);
    }
}
