package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeInUseException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.evaluation.service.EvaluationSchemeService;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EvaluationSchemeServiceTest extends AbstractIntegrationTest {

    @Autowired
    private EvaluationSchemeService evaluationSchemeService;

    @Autowired
    private SubjectSectionService subjectSectionService;

    @Autowired
    private EntityManager entityManager;

    private Subject persistSubject(String name) {
        Subject subject = new Subject();
        subject.setCode(TestSubjectCodes.next());
        subject.setName(name);
        entityManager.persist(subject);
        return subject;
    }

    private Term persistTerm() {
        Term term = new Term();
        term.setYear(2026);
        term.setDescription("2026-1C");
        term.setStatus(StatusType.ACTIVE);
        entityManager.persist(term);
        return term;
    }

    private CreateEvaluationSchemeRequest validRequest(String name) {
        return new CreateEvaluationSchemeRequest(name, 2, true, 0.6, null, 4.0, 6.0, 8.0, null);
    }

    @Test
    void createPersistsANewScheme() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento 2026"));

        assertThat(scheme.getId()).isNotNull();
        assertThat(scheme.getMinRegularGrade()).isEqualTo(4.0);
        assertThat(scheme.getMinApprovalGrade()).isEqualTo(6.0);
        assertThat(evaluationSchemeService.isInUse(scheme.getId())).isFalse();
    }

    @Test
    void updatingASchemeNotInUseWorks() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento viejo"));

        EvaluationScheme updated = evaluationSchemeService.update(scheme.getId(), validRequest("Reglamento corregido"));

        assertThat(updated.getName()).isEqualTo("Reglamento corregido");
    }

    @Test
    void updatingASchemeInUseFails() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento en uso"));
        Subject subject = persistSubject("MateriaConEsquemaEnUso");
        Term term = persistTerm();
        entityManager.flush();
        subjectSectionService.create(subject.getId(),
                new CreateSectionRequest("Comisión A", term.getId(), null, 30, scheme.getId()));

        assertThatThrownBy(() -> evaluationSchemeService.update(scheme.getId(), validRequest("Intento de cambio")))
                .isInstanceOf(EvaluationSchemeInUseException.class);
    }
}
