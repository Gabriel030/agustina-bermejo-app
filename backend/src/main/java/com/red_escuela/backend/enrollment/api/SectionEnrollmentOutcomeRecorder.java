package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.events.ProgramSectionStatusChangedEvent;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class SectionEnrollmentOutcomeRecorder {

    public static final String FREE_OUTCOME_REASON = "Libre por inasistencias";

    private final SubjectSectionEnrollmentRepository programEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public SectionEnrollmentOutcomeRecorder(SubjectSectionEnrollmentRepository programEnrollmentRepository,
                                            CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository,
                                            ApplicationEventPublisher eventPublisher) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void recordProgramOutcome(Long enrollmentId, EnrollmentStatus status, Grade finalScore,
                                     FinalGradeType finalGradeType, boolean clearFinalWhenAbsent) {
        SubjectSectionEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        EnrollmentStatus previousStatus = enrollment.getStatus();
        Grade previousScore = enrollment.getFinalScore();
        enrollment.setStatus(status);
        if (finalScore != null) {
            enrollment.setFinalScore(finalScore);
            enrollment.setFinalGradeType(finalGradeType);
            if (status == EnrollmentStatus.APPROVED && enrollment.getFinalScoreDate() == null) {
                enrollment.setFinalScoreDate(ApplicationTime.today());
            }
        } else if (clearFinalWhenAbsent) {
            enrollment.setFinalScore(null);
            enrollment.setFinalGradeType(null);
            enrollment.setFinalScoreDate(null);
        }
        programEnrollmentRepository.save(enrollment);
        boolean approvedOutcomeChanged = previousStatus == EnrollmentStatus.APPROVED
                && (status != EnrollmentStatus.APPROVED || !Objects.equals(previousScore, enrollment.getFinalScore()));
        if (previousStatus != status || approvedOutcomeChanged) {
            eventPublisher.publishEvent(ProgramSectionStatusChangedEvent.changed(
                    enrollment.getProgramEnrollment().getId(), enrollmentId, previousStatus != status, approvedOutcomeChanged));
        }
    }

    @Transactional
    public void recordCourseOutcome(Long enrollmentId, EnrollmentStatus status, Grade finalScore,
                                    FinalGradeType finalGradeType, boolean clearFinalWhenAbsent) {
        CourseSubjectSectionEnrollment enrollment = courseEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        enrollment.setStatus(status);
        if (finalScore != null) {
            enrollment.setFinalScore(finalScore);
            enrollment.setFinalGradeType(finalGradeType);
        } else if (clearFinalWhenAbsent) {
            enrollment.setFinalScore(null);
            enrollment.setFinalGradeType(null);
        }
        courseEnrollmentRepository.save(enrollment);
    }

    @Transactional
    public void markProgramFreeReason(Long enrollmentId) {
        programEnrollmentRepository.findById(enrollmentId).ifPresent(enrollment -> {
            enrollment.setWithdrawalReason(FREE_OUTCOME_REASON);
            programEnrollmentRepository.save(enrollment);
        });
    }

    @Transactional
    public void markCourseFreeReason(Long enrollmentId) {
        courseEnrollmentRepository.findById(enrollmentId).ifPresent(enrollment -> {
            enrollment.setWithdrawalReason(FREE_OUTCOME_REASON);
            courseEnrollmentRepository.save(enrollment);
        });
    }
}
