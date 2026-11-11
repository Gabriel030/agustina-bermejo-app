package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.SectionKind;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentFinder;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.entity.CourseSubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.evaluation.api.GraduationRequestTriggerPort;
import com.red_escuela.backend.evaluation.entity.CommissionClosureStudent;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.policy.CourseworkOutcomeCalculator;
import com.red_escuela.backend.shared.domain.Grade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClosureOutcomeApplier {

    private final SectionEnrollmentOutcomeRecorder outcomeRecorder;
    private final SectionEnrollmentFinder sectionEnrollmentFinder;
    private final GraduationRequestTriggerPort graduationRequestTrigger;

    public ClosureOutcomeApplier(SectionEnrollmentOutcomeRecorder outcomeRecorder, SectionEnrollmentFinder sectionEnrollmentFinder,
            GraduationRequestTriggerPort graduationRequestTrigger) {
        this.outcomeRecorder = outcomeRecorder;
        this.sectionEnrollmentFinder = sectionEnrollmentFinder;
        this.graduationRequestTrigger = graduationRequestTrigger;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void applyClosedResults(SectionKind kind, List<CommissionClosureStudent> rows) {
        for (CommissionClosureStudent row : rows) {
            if (row.getFinalCondition() == null || !stillClosable(kind, row.getEnrollmentId())) {
                continue;
            }
            switch (row.getFinalCondition()) {
                case PROMOTES -> {
                    record(kind, row.getEnrollmentId(), EnrollmentStatus.APPROVED, row.getPromotionGrade(), FinalGradeType.PROMOTION);
                    requestGraduation(kind, row.getEnrollmentId());
                }
                case GOES_TO_FINAL -> {
                    record(kind, row.getEnrollmentId(), EnrollmentStatus.REGULAR, null, null);
                    flagGraduationOutdated(kind, row.getEnrollmentId());
                }
                case FAILED -> {
                    record(kind, row.getEnrollmentId(), EnrollmentStatus.FAILED, null, null);
                    flagGraduationOutdated(kind, row.getEnrollmentId());
                }
                case FREE, PROMOTION_CONDITIONAL -> {
                }
            }
        }
    }

    private boolean stillClosable(SectionKind kind, Long enrollmentId) {
        EnrollmentStatus status;
        FinalGradeType type;
        if (kind == SectionKind.PROGRAM) {
            SubjectSectionEnrollment enrollment = sectionEnrollmentFinder.refreshProgramEnrollment(enrollmentId);
            status = enrollment.getStatus();
            type = enrollment.getFinalGradeType();
        } else {
            CourseSubjectSectionEnrollment enrollment = sectionEnrollmentFinder.refreshCourseEnrollment(enrollmentId);
            status = enrollment.getStatus();
            type = enrollment.getFinalGradeType();
        }
        return status == EnrollmentStatus.ENROLLED || status == EnrollmentStatus.IN_PROGRESS
                || status == EnrollmentStatus.REGULAR || CourseworkOutcomeCalculator.isRebuildable(status, type);
    }

    private void record(SectionKind kind, Long enrollmentId, EnrollmentStatus status, Grade score, FinalGradeType type) {
        if (kind == SectionKind.PROGRAM) {
            outcomeRecorder.recordProgramOutcome(enrollmentId, status, score, type, true);
        } else {
            outcomeRecorder.recordCourseOutcome(enrollmentId, status, score, type, true);
        }
    }

    private void requestGraduation(SectionKind kind, Long enrollmentId) {
        if (kind == SectionKind.PROGRAM) {
            graduationRequestTrigger.requestGraduationIfEligible(programEnrollmentIdOf(enrollmentId));
        }
    }

    private void flagGraduationOutdated(SectionKind kind, Long enrollmentId) {
        if (kind == SectionKind.PROGRAM) {
            graduationRequestTrigger.flagGraduationRequestOutdated(programEnrollmentIdOf(enrollmentId));
        }
    }

    private Long programEnrollmentIdOf(Long enrollmentId) {
        return sectionEnrollmentFinder.findProgramEnrollment(enrollmentId).getProgramEnrollment().getId();
    }
}
