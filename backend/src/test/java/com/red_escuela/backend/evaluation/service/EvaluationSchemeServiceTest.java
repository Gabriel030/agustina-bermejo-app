package com.red_escuela.backend.evaluation.service;

import com.red_escuela.backend.shared.domain.Grade;

import java.math.BigDecimal;

import com.red_escuela.backend.config.TestSubjectCodes;

import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.evaluation.dto.CreateEvaluationSchemeRequest;
import com.red_escuela.backend.academicstructure.dto.CreateSectionRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.evaluation.exception.EvaluationSchemeInUseException;
import com.red_escuela.backend.evaluation.exception.InvalidEvaluationSchemeException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;

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
        subject.setStatus(SubjectStatus.ACTIVE);
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
        return new CreateEvaluationSchemeRequest(name, 2, true, 0.6, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(6.0), BigDecimal.valueOf(8.0), null);
    }

    @Test
    void createPersistsANewScheme() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento 2026"));

        assertThat(scheme.getId()).isNotNull();
        assertThat(scheme.getMinRegularGrade()).isEqualTo(Grade.of(BigDecimal.valueOf(4.0)));
        assertThat(scheme.getMinApprovalGrade()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
        assertThat(evaluationSchemeService.isInUse(scheme.getId())).isFalse();
    }

    @Test
    void updatingASchemeNotInUseWorks() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento viejo"));

        EvaluationScheme updated = evaluationSchemeService.update(scheme.getId(), validRequest("Reglamento corregido"));

        assertThat(updated.getName()).isEqualTo("Reglamento corregido");
    }

    @Test
    void creatingASchemeWithGradesOutsideTheZeroToTenScaleFails() {
        assertThatThrownBy(() -> evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Notas fuera de escala", 2, true, null, null, BigDecimal.valueOf(-2.0), BigDecimal.valueOf(6.0), null, null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
        assertThatThrownBy(() -> evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Final sobre 15", 2, true, null, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(15.0), null, null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
    }

    @Test
    void creatingASchemeWhosePromotionCutIsBelowTheApprovalGradeFails() {
        assertThatThrownBy(() -> evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Promoción con 3", 2, true, null, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(6.0), BigDecimal.valueOf(3.0), null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
    }

    @Test
    void creatingASchemeWhoseApprovalGradeIsBelowTheRegularGradeFails() {
        assertThatThrownBy(() -> evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Aprobar con menos que regular", 2, true, null, null,
                        BigDecimal.valueOf(6.0), BigDecimal.valueOf(4.0), null, null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
    }

    @Test
    void creatingASchemeWithMoreThanFourExamsFails() {
        assertThatThrownBy(() -> evaluationSchemeService.create(
                new CreateEvaluationSchemeRequest("Cinco parciales", 5, true, null, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(6.0), null, null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
    }

    @Test
    void updatingASchemeWithIncoherentGradesFailsAndKeepsThePreviousValues() {
        EvaluationScheme scheme = evaluationSchemeService.create(validRequest("Reglamento a corregir"));

        assertThatThrownBy(() -> evaluationSchemeService.update(scheme.getId(),
                new CreateEvaluationSchemeRequest("Reglamento a corregir", 2, true, null, null, BigDecimal.valueOf(4.0), BigDecimal.valueOf(11.0), null, null)))
                .isInstanceOf(InvalidEvaluationSchemeException.class);
        assertThat(scheme.getMinApprovalGrade()).isEqualTo(Grade.of(BigDecimal.valueOf(6.0)));
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
