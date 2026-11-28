package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.dto.UpdateSectionRequest;
import com.red_escuela.backend.academicstructure.dto.UpdateSubjectRequest;
import com.red_escuela.backend.academicstructure.entity.GradeMode;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.SubjectStatus;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.SubjectSectionService;
import com.red_escuela.backend.academicstructure.service.SubjectService;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.shared.domain.Grade;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationRulesImmutabilityReproductionIT extends ReproductionFixture {

    @Autowired
    private SubjectSectionService subjectSectionService;
    @Autowired
    private SubjectService subjectService;

    private EvaluationScheme scheme(String name) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName(name);
        scheme.setExamCountExpected(2);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(false);
        scheme.setMinRegularGrade(Grade.of(new BigDecimal("4")));
        scheme.setMinApprovalGrade(Grade.of(new BigDecimal("7")));
        scheme.setPromotionMinGrade(Grade.of(new BigDecimal("7")));
        entityManager.persist(scheme);
        return scheme;
    }

    @Test
    void sectionEvaluationSchemeCannotBeReassignedOnceGradesAreLoaded() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSection section = section(subject, term);
        section.setEvaluationScheme(scheme("Esquema original"));
        EvaluationScheme other = scheme("Esquema nuevo");
        ProgramEnrollment programEnrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = sectionEnrollment(programEnrollment, section, EnrollmentStatus.IN_PROGRESS);
        GradeItem item = new GradeItem();
        item.setProgramSubjectSectionEnrollment(enrollment);
        item.setType(GradeItemType.EXAM);
        item.setSequenceNumber(1);
        item.setScore(Grade.of(new BigDecimal("8.00")));
        item.setDate(LocalDateTime.of(2026, 4, 1, 10, 0));
        entityManager.persist(item);
        flushAndClear();

        UpdateSectionRequest request = new UpdateSectionRequest(null, null, null, other.getId(), null, null, null);

        assertThatThrownBy(() -> subjectSectionService.update(section.getId(), request)).isInstanceOf(RuntimeException.class);
    }

    @Test
    void gradingRulesOfAnApprovedSubjectCannotBeEdited() {
        Subject subject = subject("Algebra");
        subject.setStatus(SubjectStatus.ACTIVE);
        subject.setGradeMode(GradeMode.NUMERIC);
        flushAndClear();

        UpdateSubjectRequest request = new UpdateSubjectRequest(null, null, null, null, GradeMode.PASS_FAIL, 9, null);

        assertThatThrownBy(() -> subjectService.update(subject.getId(), request)).isInstanceOf(RuntimeException.class);
    }
}
