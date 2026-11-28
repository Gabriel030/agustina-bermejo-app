package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.shared.domain.Grade;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SectionEvaluationSchemeLoaderIT extends AbstractIntegrationTest {

    @Autowired
    private SectionEvaluationSchemeLoader loader;

    @Autowired
    private EntityManager entityManager;

    @Test
    void loadsThePersistedSchemeBehindAnUninitializedReference() {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("loader-it");
        scheme.setExamCountExpected(2);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        entityManager.flush();
        entityManager.clear();
        SubjectSection section = new SubjectSection();
        section.setEvaluationSchemeId(scheme.getId());

        EvaluationScheme loaded = loader.ofProgramSection(section);

        assertThat(loaded.getId()).isEqualTo(scheme.getId());
        assertThat(loaded.getExamCountExpected()).isEqualTo(2);
    }

    @Test
    void returnsNullWhenTheReferencedSchemeDoesNotExist() {
        SubjectSection section = new SubjectSection();
        section.setEvaluationSchemeId(Long.MAX_VALUE);

        assertThat(loader.ofProgramSection(section)).isNull();
    }
}
