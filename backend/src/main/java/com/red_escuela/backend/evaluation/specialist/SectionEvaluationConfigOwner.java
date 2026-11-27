package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.evaluation.domain.SectionRef;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;

public final class SectionEvaluationConfigOwner {

    private SectionEvaluationConfigOwner() {
    }

    public static SectionRef sectionRefOf(SectionEvaluationConfig config) {
        return config.isOwnedByCourseSection()
                ? SectionRef.course(config.ownerSectionId())
                : SectionRef.program(config.ownerSectionId());
    }
}
