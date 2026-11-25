package com.red_escuela.backend.evaluation.usecase.sectionconfig;

import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

import java.time.LocalDate;

public record SectionEvaluationConfigWithDeadline(SectionEvaluationConfig config, LocalDate deadline) {

    public static SectionEvaluationConfigWithDeadline of(SectionEvaluationConfig config, int dueDays) {
        var termStart = config.getSubjectSection().getTerm().getStartDate();
        return new SectionEvaluationConfigWithDeadline(config, termStart == null ? null : termStart.toLocalDate().plusDays(dueDays));
    }
}
