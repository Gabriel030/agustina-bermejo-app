package com.red_escuela.backend.testsupport;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfig;
import com.red_escuela.backend.evaluation.entity.SectionEvaluationConfigStatus;
import jakarta.persistence.EntityManager;

public final class ApprovedSectionConfigFixture {

    private ApprovedSectionConfigFixture() {
    }

    public static void persistWhenSchemeExpectsCoursework(EntityManager entityManager, SubjectSection section) {
        EvaluationScheme scheme = section.getEvaluationSchemeId() == null ? null : entityManager.find(EvaluationScheme.class, section.getEvaluationSchemeId());
        if (scheme != null && scheme.getTpCountExpected() != null && scheme.getTpCountExpected() > 0) {
            persist(entityManager, section, scheme.getTpCountExpected());
        }
    }

    public static SectionEvaluationConfig persist(EntityManager entityManager, SubjectSection section, int tpCount) {
        SectionEvaluationConfig config = new SectionEvaluationConfig();
        config.setSubjectSection(section);
        config.setStatus(SectionEvaluationConfigStatus.APPROVED);
        config.setTpCount(tpCount);
        entityManager.persist(config);
        return config;
    }
}
