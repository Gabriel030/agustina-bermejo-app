package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.api.ProgramCurriculumFinder;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.ProgramProgressView;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.service.CurriculumProgressReader;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.service.ProgramPlanSubjectGuard;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProgressReadersDisagreeReproductionIT extends ReproductionFixture {

    @Autowired
    private ProgramProgressReader programProgressReader;
    @Autowired
    private CurriculumProgressReader curriculumProgressReader;
    @Autowired
    private ProgramCurriculumFinder programCurriculumFinder;
    @Autowired
    private ProgramPlanSubjectGuard programPlanSubjectGuard;

    private Program program;
    private Subject inactiveSubject;
    private ProgramEnrollment enrollment;

    @BeforeEach
    void planWithOneApprovedSubjectAndOneInactiveSubject() {
        program = program(4);
        Subject active = subject("Algebra");
        inactiveSubject = subject("Historia desactivada");
        Subject outsideThePlan = subject("Materia fuera del plan");
        programSubject(program, active, 4, StatusType.ACTIVE);
        programSubject(program, inactiveSubject, 2, StatusType.INACTIVE);
        enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        approve(active, term, "8.00");
        approve(outsideThePlan, term, "2.00");
        flushAndClear();
    }

    private void approve(Subject subject, Term term, String score) {
        SubjectSection section = section(subject, term);
        SubjectSectionEnrollment sectionEnrollment = sectionEnrollment(enrollment, section, EnrollmentStatus.APPROVED);
        sectionEnrollment.setFinalScore(Grade.of(new BigDecimal(score)));
    }

    @Test
    void inactivePlanSubjectDoesNotBlockProgramCompletionWhenCurriculumViewShowsFullProgress() {
        CurriculumProgressView.Summary summary = curriculumProgressReader.readProgress(enrollment.getId()).summary();
        ProgramProgressView progress = programProgressReader.calculateProgress(enrollment.getId());

        assertThat(summary.completionPercent().points()).isEqualByComparingTo("100");
        assertThat(progress.pendingMandatorySubjects()).isEmpty();
        assertThat(progress.eligibleToComplete()).isTrue();
    }

    @Test
    void overallAverageOnlyCountsApprovedSubjectsOfThePlanLikeTheCurriculumView() {
        CurriculumProgressView.Summary summary = curriculumProgressReader.readProgress(enrollment.getId()).summary();
        ProgramProgressView progress = programProgressReader.calculateProgress(enrollment.getId());

        assertThat(summary.averageScore().value()).isEqualByComparingTo("8.00");
        assertThat(progress.overallAverage()).isEqualTo(summary.averageScore());
    }

    @Test
    void planGuardRejectsSubjectDeactivatedInThePlan() {
        assertThatThrownBy(() -> programPlanSubjectGuard.requireSubjectInEnrollmentPlan(enrollment, inactiveSubject.getId()))
                .isInstanceOf(com.red_escuela.backend.enrollment.exception.SubjectNotInEnrollmentPlanException.class);
    }

    @Test
    void subjectCountUsedByApprovedSubjectsCertificateExcludesInactivePlanSubjects() {
        int totalSubjectsUsedByCertificate = programCurriculumFinder.findActiveProgramSubjectsOfPlan(enrollment.getPlan().getId()).size();

        assertThat(totalSubjectsUsedByCertificate)
                .isEqualTo(programCurriculumFinder.findActiveCurriculumEntries(program.getId()).size());
    }
}
