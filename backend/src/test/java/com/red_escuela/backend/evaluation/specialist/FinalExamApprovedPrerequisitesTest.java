package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.ProgramSubjectPrerequisite;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.GradeAggregationMethod;
import com.red_escuela.backend.evaluation.exception.ExamBoardFinalGradeRowRejectedException;
import com.red_escuela.backend.evaluation.exception.FinalExamPrerequisitesNotApprovedException;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FinalExamApprovedPrerequisitesTest extends ExamBoardChangeFixture {

    private static final String PREREQUISITE_NAME = "Algebra previa";

    @Autowired
    private ExamBoardRegistrationManager registrationManager;

    @Autowired
    private ExamBoardFinalGradesRecorder finalGradesRecorder;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    private record Setup(ExamBoard board, Instructor tribunal, SubjectSectionEnrollment enrollment, Subject subject) {
    }

    private Setup setup(String dni, EnrollmentStatus prerequisiteStatus, LocalDateTime examDate) {
        Subject subject = subject("Materia con correlativa " + dni, null);
        Term term = term();
        ExamBoard board = examBoard(subject, term, examDate);
        Instructor tribunal = tribunalMember(board, dni);
        SubjectSectionEnrollment enrollment = regularEnrollment(subject, term, dni + "1", "Carrera " + dni);
        enrollment.getSubjectSection().setEvaluationScheme(scheme(dni));
        Program program = enrollment.getProgramEnrollment().getProgram();
        Subject prerequisite = subject(PREREQUISITE_NAME, null);
        ProgramSubject target = programSubject(program, subject);
        ProgramSubject required = programSubject(program, prerequisite);
        ProgramSubjectPrerequisite link = new ProgramSubjectPrerequisite();
        link.setProgramSubject(target);
        link.setPrerequisiteProgramSubject(required);
        link.setRequirementType(RequirementType.PASSED_COURSEWORK);
        entityManager.persist(link);
        studentTook(enrollment, prerequisite, term, prerequisiteStatus);
        entityManager.flush();
        return new Setup(board, tribunal, enrollment, subject);
    }

    private EvaluationScheme scheme(String dni) {
        EvaluationScheme scheme = new EvaluationScheme();
        scheme.setName("Esquema " + dni);
        scheme.setExamCountExpected(0);
        scheme.setTpCountExpected(0);
        scheme.setHasFinal(true);
        scheme.setAggregationMethod(GradeAggregationMethod.SIMPLE_AVERAGE);
        scheme.setMinRegularGrade(Grade.of(BigDecimal.valueOf(4.0)));
        scheme.setMinApprovalGrade(Grade.of(BigDecimal.valueOf(6.0)));
        entityManager.persist(scheme);
        return scheme;
    }

    private ProgramSubject programSubject(Program program, Subject subject) {
        ProgramSubject programSubject = new ProgramSubject();
        programSubject.setProgram(program);
        programSubject.setSubject(subject);
        programSubject.setCredits(10);
        programSubject.setCurricularYear(1);
        programSubject.setCurricularTerm(CurricularTerm.FIRST);
        programSubject.setStatus(StatusType.ACTIVE);
        entityManager.persist(programSubject);
        return programSubject;
    }

    private void studentTook(SubjectSectionEnrollment enrollment, Subject prerequisite, Term term, EnrollmentStatus status) {
        SubjectSection section = new SubjectSection();
        section.setName(prerequisite.getName() + " - Com A");
        section.setSubject(prerequisite);
        section.setTerm(term);
        section.setStatus(StatusType.ACTIVE);
        entityManager.persist(section);
        SubjectSectionEnrollment taken = new SubjectSectionEnrollment();
        taken.setStudent(enrollment.getStudent());
        taken.setProgramEnrollment(enrollment.getProgramEnrollment());
        taken.setSubjectSection(section);
        taken.setStatus(status);
        taken.setEnrolledAt(LocalDateTime.now().minusYears(1));
        taken.setAttemptNumber(1);
        entityManager.persist(taken);
    }

    @Test
    void registrationIsRejectedWhenAPrerequisiteIsOnlyRegularAndNamesIt() {
        Setup setup = setup("75000001", EnrollmentStatus.REGULAR, LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> registrationManager.registerProgramEnrollment(
                setup.board().getId(), setup.enrollment().getId()))
                .isInstanceOf(FinalExamPrerequisitesNotApprovedException.class)
                .satisfies(failure -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> first = (Map<String, Object>) ((List<?>) ((FinalExamPrerequisitesNotApprovedException) failure)
                            .details().get("pendingPrerequisites")).get(0);
                    assertThat(first)
                            .containsEntry("subjectName", PREREQUISITE_NAME)
                            .containsEntry("standing", "REGULAR");
                });
    }

    @Test
    void registrationIsAcceptedWhenThePrerequisiteIsApproved() {
        Setup setup = setup("75000011", EnrollmentStatus.APPROVED, LocalDateTime.now().plusDays(1));

        ExamBoardRegistration registration = registrationManager.registerProgramEnrollment(
                setup.board().getId(), setup.enrollment().getId());

        assertThat(registration.getId()).isNotNull();
    }

    @Test
    void batchRowIsRejectedWhenAPrerequisiteIsOnlyRegularAndNothingIsSaved() {
        Setup setup = setup("75000021", EnrollmentStatus.REGULAR, LocalDateTime.now().minusDays(1));
        ExamBoardRegistration registration = registration(setup.board(), setup.enrollment());
        entityManager.flush();

        assertThatThrownBy(() -> finalGradesRecorder.record(setup.board().getId(), setup.tribunal().getId(),
                List.of(new ExamBoardFinalGradeEntryRequest(registration.getId(), Grade.of(new BigDecimal("8")), null, null))))
                .isInstanceOf(ExamBoardFinalGradeRowRejectedException.class)
                .satisfies(failure -> assertThat(((ExamBoardFinalGradeRowRejectedException) failure).details())
                        .containsEntry("registrationId", registration.getId())
                        .containsEntry("causeCode", "FINAL_EXAM_PREREQUISITES_NOT_APPROVED"));
        assertThat(gradeItemRecorder.listByProgramEnrollment(setup.enrollment().getId())).isEmpty();
    }

    @Test
    void batchLoadsTheFinalWhenThePrerequisiteIsApproved() {
        Setup setup = setup("75000031", EnrollmentStatus.APPROVED, LocalDateTime.now().minusDays(1));
        ExamBoardRegistration registration = registration(setup.board(), setup.enrollment());
        entityManager.flush();

        finalGradesRecorder.record(setup.board().getId(), setup.tribunal().getId(),
                List.of(new ExamBoardFinalGradeEntryRequest(registration.getId(), Grade.of(new BigDecimal("8")), null, null)));

        assertThat(gradeItemRecorder.listByProgramEnrollment(setup.enrollment().getId())).hasSize(1);
    }
}
