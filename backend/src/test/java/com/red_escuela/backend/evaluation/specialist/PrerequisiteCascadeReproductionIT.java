package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.domain.CurricularTerm;
import com.red_escuela.backend.academicstructure.domain.RequirementType;
import com.red_escuela.backend.academicstructure.dto.AddPrerequisiteRequest;
import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.academicstructure.service.ProgramSubjectService;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.EvaluationScheme;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PrerequisiteCascadeReproductionIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private GradeItemRecorder gradeItemRecorder;

    @Autowired
    private SectionGradeItemPublisher sectionGradeItemPublisher;

    @Autowired
    private ProgramSubjectService programSubjectService;

    private AcademicReproductionFixtures fixtures;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
    }

    private record Chain(Instructor instructor, SubjectSection firstSection, SubjectSection secondSection,
                         SubjectSectionEnrollment first, SubjectSectionEnrollment second) {
    }

    private Chain chain(String dni) {
        Program program = fixtures.program("Carrera" + dni);
        Subject firstSubject = fixtures.subject("Primera" + dni);
        Subject secondSubject = fixtures.subject("Segunda" + dni);
        ProgramSubject firstEntry = fixtures.programSubject(program, firstSubject, 1, CurricularTerm.FIRST);
        ProgramSubject secondEntry = fixtures.programSubject(program, secondSubject, 2, CurricularTerm.FIRST);
        entityManager.flush();
        programSubjectService.addPrerequisite(secondEntry.getId(),
                new AddPrerequisiteRequest(firstEntry.getId(), RequirementType.APPROVED, null, null));
        Term term = fixtures.openTerm();
        Instructor instructor = fixtures.instructor(dni);
        SubjectSection firstSection = fixtures.section(firstSubject, term, fixtures.scheme(1, 0, true));
        EvaluationScheme promotingScheme = fixtures.scheme(2, 0, false);
        promotingScheme.setPromotionMinGrade(Grade.of(BigDecimal.valueOf(7.0)));
        SubjectSection secondSection = fixtures.section(secondSubject, term, promotingScheme);
        fixtures.assignInstructor(firstSection, instructor);
        fixtures.assignInstructor(secondSection, instructor);
        Student student = fixtures.student(dni + "1");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, program, ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment first = fixtures.sectionEnrollment(student, programEnrollment, firstSection, EnrollmentStatus.REGULAR);
        SubjectSectionEnrollment second = fixtures.sectionEnrollment(student, programEnrollment, secondSection, EnrollmentStatus.IN_PROGRESS);
        entityManager.flush();
        return new Chain(instructor, firstSection, secondSection, first, second);
    }

    private void loadAndPublishPromotingExams(Chain chain) {
        for (int sequence = 1; sequence <= 2; sequence++) {
            gradeItemRecorder.loadProgramEnrollmentGrade(chain.second().getId(), chain.instructor().getId(),
                    new CreateGradeItemRequest(GradeItemType.EXAM, sequence, null, LocalDateTime.now(),
                            BigDecimal.valueOf(9.0), null, null, null));
            entityManager.flush();
            sectionGradeItemPublisher.publishProgramSection(chain.secondSection().getId(), chain.instructor().getId(),
                    chain.instructor().getPerson().getId(), new PublishGradeItemRequest(GradeItemType.EXAM, sequence));
            entityManager.flush();
        }
    }

    private ExamBoard boardWithRegistration(Chain chain) {
        ExamBoard board = new ExamBoard();
        board.setSubject(chain.firstSection().getSubject());
        board.setTerm(chain.firstSection().getTerm());
        board.setExamDate(LocalDateTime.now().minusHours(2));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        ExamBoardInstructor tribunal = new ExamBoardInstructor();
        tribunal.setExamBoard(board);
        tribunal.setInstructor(chain.instructor());
        entityManager.persist(tribunal);
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(chain.first());
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        entityManager.persist(registration);
        entityManager.flush();
        return board;
    }

    private void loadFirstFinal(Chain chain, ExamBoard board, double score, String reason) {
        gradeItemRecorder.loadProgramEnrollmentGrade(chain.first().getId(), chain.instructor().getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(score),
                        null, board.getId(), reason));
        entityManager.flush();
    }

    @Test
    @Disabled("E34-A-S1")
    void h10ApprovingThePrerequisiteLateRecoversThePromotionOfTheDependentSubject() {
        Chain chain = chain("78000001");
        loadAndPublishPromotingExams(chain);
        entityManager.refresh(chain.second());
        assertThat(chain.second().getStatus()).isEqualTo(EnrollmentStatus.REGULAR);
        ExamBoard board = boardWithRegistration(chain);

        loadFirstFinal(chain, board, 8.0, null);
        entityManager.refresh(chain.first());
        entityManager.refresh(chain.second());

        assertThat(chain.first().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        assertThat(chain.second().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    @Disabled("E34-A-S1")
    void h10RevertingThePrerequisiteApprovalReviewsTheAlreadyApprovedDependentSubject() {
        Chain chain = chain("78000003");
        ExamBoard board = boardWithRegistration(chain);
        loadFirstFinal(chain, board, 8.0, null);
        loadAndPublishPromotingExams(chain);
        entityManager.refresh(chain.second());
        assertThat(chain.second().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);

        loadFirstFinal(chain, board, 2.0, "Error de carga en el acta de la primera materia");
        entityManager.refresh(chain.first());
        entityManager.refresh(chain.second());

        assertThat(chain.first().getStatus()).isNotEqualTo(EnrollmentStatus.APPROVED);
        assertThat(chain.second().getStatus()).isNotEqualTo(EnrollmentStatus.APPROVED);
    }
}
