package com.red_escuela.backend.e34b;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGrade;
import com.red_escuela.backend.enrollment.api.ApprovedSubjectGradesReader;
import com.red_escuela.backend.enrollment.api.CurriculumProgressView;
import com.red_escuela.backend.enrollment.api.ProgramProgressReader;
import com.red_escuela.backend.enrollment.api.SectionEnrollmentOutcomeRecorder;
import com.red_escuela.backend.enrollment.api.SubjectApprovalLookup;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.FinalGradeType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.repository.SubjectSectionEnrollmentRepository;
import com.red_escuela.backend.enrollment.service.CurriculumProgressReader;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApprovedOutcomeCriteriaReproductionIT extends ReproductionFixture {

    @Autowired
    private SectionEnrollmentOutcomeRecorder outcomeRecorder;
    @Autowired
    private SubjectSectionEnrollmentRepository sectionEnrollmentRepository;
    @Autowired
    private ApprovedSubjectGradesReader approvedSubjectGradesReader;
    @Autowired
    private CurriculumProgressReader curriculumProgressReader;
    @Autowired
    private ProgramProgressReader programProgressReader;
    @Autowired
    private SubjectApprovalLookup subjectApprovalLookup;

    private static Grade grade(String value) {
        return Grade.of(new BigDecimal(value));
    }

    @Test
    void recordingAnApprovedProgramOutcomeStoresTheFinalScoreDateUsedByTheCertificate() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        programSubject(program, subject, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        Term term = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSectionEnrollment sectionEnrollment = sectionEnrollment(enrollment, section(subject, term), EnrollmentStatus.IN_PROGRESS);
        flushAndClear();

        outcomeRecorder.recordProgramOutcome(sectionEnrollment.getId(), EnrollmentStatus.APPROVED, grade("8.00"),
                FinalGradeType.PROMOTION, false);
        flushAndClear();

        assertThat(sectionEnrollmentRepository.findById(sectionEnrollment.getId()).orElseThrow().getFinalScoreDate()).isNotNull();
        List<ApprovedSubjectGrade> certificateLines = approvedSubjectGradesReader
                .listApprovedSubjectGrades(enrollment.getStudent().getId(), enrollment.getId());
        assertThat(certificateLines).hasSize(1);
        assertThat(certificateLines.get(0).approvedOn()).isNotNull();
    }

    @Test
    void everyReaderPicksTheSameScoreWhenASubjectHasTwoApprovedAttempts() {
        Program program = program(4);
        Subject subject = subject("Algebra");
        programSubject(program, subject, 4, StatusType.ACTIVE);
        ProgramEnrollment enrollment = programEnrollment(student(), program, ProgramEnrollmentStatus.ENROLLED);
        Term firstTerm = term(LocalDateTime.of(2025, 3, 1, 0, 0), LocalDateTime.of(2025, 7, 1, 0, 0));
        Term secondTerm = term(LocalDateTime.of(2026, 3, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0));
        SubjectSection firstSection = section(subject, firstTerm);
        SubjectSection secondSection = section(subject, secondTerm);
        SubjectSectionEnrollment first = sectionEnrollment(enrollment, firstSection, EnrollmentStatus.APPROVED);
        first.setFinalScore(grade("6.00"));
        first.setFinalScoreDate(LocalDate.of(2025, 7, 1));
        SubjectSectionEnrollment second = sectionEnrollment(enrollment, secondSection, EnrollmentStatus.APPROVED);
        second.setAttemptNumber(2);
        second.setFinalScore(grade("9.00"));
        second.setFinalScoreDate(LocalDate.of(2026, 7, 1));
        flushAndClear();

        Grade curriculumScore = curriculumProgressReader.readProgress(enrollment.getId()).subjects().get(0).finalScore();
        Grade prerequisiteScore = subjectApprovalLookup.findHistory(enrollment.getId()).get(subject.getId()).scoreForPrerequisiteCheck();
        Grade certificateScore = approvedSubjectGradesReader
                .listApprovedSubjectGrades(enrollment.getStudent().getId(), enrollment.getId()).get(0).grade();
        Grade programAverage = programProgressReader.calculateProgress(enrollment.getId()).overallAverage();

        assertThat(List.of(prerequisiteScore, certificateScore, programAverage)).containsOnly(curriculumScore);
    }
}
