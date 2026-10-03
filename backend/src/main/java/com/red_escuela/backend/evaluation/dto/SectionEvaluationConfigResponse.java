package com.red_escuela.backend.evaluation.dto;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SectionEvaluationConfigResponse(
        Long id, Long subjectSectionId, String subjectName, String sectionName, String status,
        Integer tpCount,
        LocalDate tp1DueDate, LocalDate tp2DueDate, LocalDate tp3DueDate,
        LocalDate exam1Date, LocalDate exam2Date, LocalDate exam3Date, LocalDate exam4Date,
        LocalDate finalCall1Date, LocalDate finalCall2Date,
        String rejectionReason, LocalDateTime submittedAt,
        String reviewedByName, LocalDateTime reviewedAt,
        LocalDateTime termStartDate, LocalDateTime termEndDate, LocalDateTime termEnrollmentStart,
        String evaluationSchemeName, Integer examCountExpected, Boolean hasFinal,
        Double minRegularGrade, Double minApprovalGrade, Double promotionMinGrade,
        List<SectionInstructorSummary> instructors, boolean canEdit, boolean canSubmit) {

    public static SectionEvaluationConfigResponse from(SectionEvaluationConfig config, List<SectionInstructorSummary> instructors) {
        return from(config, instructors, false, false);
    }

    public static SectionEvaluationConfigResponse from(SectionEvaluationConfig config, List<SectionInstructorSummary> instructors,
                                                       boolean canEdit, boolean canSubmit) {
        var section = config.getSubjectSection();
        var term = section.getTerm();
        var scheme = section.getEvaluationScheme();
        return new SectionEvaluationConfigResponse(
                config.getId(), section.getId(), section.getSubject().getName(), section.getName(),
                config.getStatus().name(),
                config.getTpCount(),
                config.getTp1DueDate(), config.getTp2DueDate(), config.getTp3DueDate(),
                config.getExam1Date(), config.getExam2Date(), config.getExam3Date(), config.getExam4Date(),
                config.getFinalCall1Date(), config.getFinalCall2Date(),
                config.getRejectionReason(), config.getSubmittedAt(),
                config.getReviewedBy() == null ? null : config.getReviewedBy().getName() + " " + config.getReviewedBy().getLastname(),
                config.getReviewedAt(),
                term.getStartDate(), term.getEndDate(), term.getEnrollmentStart(),
                scheme == null ? null : scheme.getName(),
                scheme == null ? null : scheme.getExamCountExpected(),
                scheme == null ? null : scheme.getHasFinal(),
                scheme == null ? null : scheme.getMinRegularGrade(),
                scheme == null ? null : scheme.getMinApprovalGrade(),
                scheme == null ? null : scheme.getPromotionMinGrade(),
                instructors, canEdit, canSubmit);
    }
}
