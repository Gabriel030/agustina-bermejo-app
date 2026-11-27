package com.red_escuela.backend.enrollment.api;

import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.SectionEnrollmentNotFoundException;
import com.red_escuela.backend.enrollment.repository.CourseSubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class SectionEnrollmentOutcomeRecorder {

    public static final String FREE_OUTCOME_REASON = "Libre por inasistencias";

    private final SubjectSectionEnrollmentRepository programEnrollmentRepository;
    private final CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository;

    public SectionEnrollmentOutcomeRecorder(SubjectSectionEnrollmentRepository programEnrollmentRepository,
                                            CourseSubjectSectionEnrollmentRepository courseEnrollmentRepository) {
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional
    public void recordProgramOutcome(Long enrollmentId, EnrollmentStatus status, Grade finalScore,
                                     FinalGradeType finalGradeType, boolean clearFinalWhenAbsent) {
        SubjectSectionEnrollment enrollment = programEnrollmentRepository.findById(enrollmentId)
                .orElseThrow(() -> new SectionEnrollmentNotFoundException(enrollmentId));
        enrollment.setStatus(status);
        if (finalScore != null) {
            enrollment.setFinalScore(finalScore);
            enrollment.setFinalGradeType(finalGradeType);
        } else if (clearFinalWhenAbsent) {
            enrollment.setFinalScore(null);
            enrollment.setFinalGradeType(null);
        }
        programEnrollmentRepository.save(enrollment);
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
    public void recordProgramFreeOutcome(Long enrollmentId) {
        recordProgramOutcome(enrollmentId, EnrollmentStatus.FAILED, null, null, true);
        programEnrollmentRepository.findById(enrollmentId).ifPresent(enrollment -> {
            enrollment.setWithdrawalReason(FREE_OUTCOME_REASON);
            programEnrollmentRepository.save(enrollment);
        });
    }

    @Transactional
    public void recordCourseFreeOutcome(Long enrollmentId) {
        recordCourseOutcome(enrollmentId, EnrollmentStatus.FAILED, null, null, true);
        courseEnrollmentRepository.findById(enrollmentId).ifPresent(enrollment -> {
            enrollment.setWithdrawalReason(FREE_OUTCOME_REASON);
            courseEnrollmentRepository.save(enrollment);
        });
    }
}
