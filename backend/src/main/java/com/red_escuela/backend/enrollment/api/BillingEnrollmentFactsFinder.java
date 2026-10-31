package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingQuery;
import com.red_escuela.backend.billing.api.EnrollmentPaymentStandingView;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Optional;

@Component
public class BillingEnrollmentFactsFinder {
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EnrollmentPaymentStandingQuery standingQuery;

    public BillingEnrollmentFactsFinder(ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                                              EnrollmentPaymentStandingQuery standingQuery) {
        this.standingQuery = standingQuery;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional(readOnly = true)
    public Optional<BillingEnrollmentFacts> findProgramEnrollmentFacts(Long programEnrollmentId) {
        Optional<ProgramEnrollmentRepository.BillingTemplateRef> ref = programEnrollmentRepository.findBillingTemplateRef(programEnrollmentId);
        return programEnrollmentRepository.findById(programEnrollmentId)
                .map(enrollment -> new BillingEnrollmentFacts(enrollment.getStudent().getId(), enrollment.getProgram().getCurrency(), enrollment.getProgram().getId(),
                        ref.map(ProgramEnrollmentRepository.BillingTemplateRef::getTemplateId).orElse(null), null, null, programEnrollmentRepository.existsByIdAndStatus(programEnrollmentId, ProgramEnrollmentStatus.ENROLLED)
                                && !standingQuery.findStandingOfProgramEnrollment(programEnrollmentId).map(EnrollmentPaymentStandingView::stopsBilling).orElse(false)));
    }

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
