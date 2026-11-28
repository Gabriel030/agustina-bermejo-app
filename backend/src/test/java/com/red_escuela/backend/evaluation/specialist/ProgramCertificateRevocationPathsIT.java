package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.entity.Instructor;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.e34b.IssuedCertificateRows;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequest;
import com.red_escuela.backend.enrollment.entity.DirectorApprovalRequestStatus;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ExceptionType;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.enrollment.exception.ProgramEnrollmentRequiredException;
import com.red_escuela.backend.enrollment.repository.DirectorApprovalRequestRepository;
import com.red_escuela.backend.enrollment.service.ProgramEnrollmentService;
import com.red_escuela.backend.evaluation.dto.CorrectGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.CreateGradeItemRequest;
import com.red_escuela.backend.evaluation.dto.ExamBoardFinalGradeEntryRequest;
import com.red_escuela.backend.evaluation.dto.PublishGradeItemRequest;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.entity.GradeItem;
import com.red_escuela.backend.evaluation.entity.GradeItemType;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.testsupport.AcademicReproductionFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProgramCertificateRevocationPathsIT extends AbstractIntegrationTest {

    private static final String CORRECTION_REASON = "Error de carga detectado en la revision";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private GradeItemRecorder gradeItemRecorder;
    @Autowired
    private SectionGradeItemPublisher sectionGradeItemPublisher;
    @Autowired
    private PublishedGradeCorrector publishedGradeCorrector;
    @Autowired
    private ProgramEnrollmentService programEnrollmentService;
    @Autowired
    private ExamBoardFinalGradesRecorder examBoardFinalGradesRecorder;
    @Autowired
    private DirectorApprovalRequestRepository directorApprovalRequestRepository;

    private AcademicReproductionFixtures fixtures;
    private IssuedCertificateRows rows;

    @BeforeEach
    void createFixtures() {
        fixtures = new AcademicReproductionFixtures(entityManager);
        rows = new IssuedCertificateRows(entityManager);
    }

    private record Setup(Instructor instructor, SubjectSection section, SubjectSectionEnrollment enrollment) {
    }

    private Setup setup(String dni, int examCount, boolean hasFinal, EnrollmentStatus status) {
        Instructor instructor = fixtures.instructor(dni);
        SubjectSection section = fixtures.section(fixtures.subject("Materia" + dni), fixtures.openTerm(),
                fixtures.scheme(examCount, 0, hasFinal));
        fixtures.assignInstructor(section, instructor);
        Student student = fixtures.student(dni + "1");
        ProgramEnrollment programEnrollment = fixtures.programEnrollment(student, fixtures.program("Carrera" + dni),
                ProgramEnrollmentStatus.ENROLLED);
        SubjectSectionEnrollment enrollment = fixtures.sectionEnrollment(student, programEnrollment, section, status);
        entityManager.flush();
        return new Setup(instructor, section, enrollment);
    }

    private SubjectSectionEnrollment secondStudent(Setup setup, String dni) {
        SubjectSectionEnrollment other = fixtures.enrollInSection(dni, setup.section());
        other.setStatus(EnrollmentStatus.REGULAR);
        entityManager.flush();
        return other;
    }

    private ExamBoard board(Setup setup) {
        ExamBoard board = new ExamBoard();
        board.setSubject(setup.section().getSubject());
        board.setTerm(setup.section().getTerm());
        board.setExamDate(LocalDateTime.now().minusHours(2));
        board.setStatus(ExamBoardStatus.SCHEDULED);
        entityManager.persist(board);
        ExamBoardInstructor tribunal = new ExamBoardInstructor();
        tribunal.setExamBoard(board);
        tribunal.setInstructor(setup.instructor());
        entityManager.persist(tribunal);
        entityManager.flush();
        return board;
    }

    private ExamBoardRegistration register(ExamBoard board, SubjectSectionEnrollment enrollment) {
        ExamBoardRegistration registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.now());
        entityManager.persist(registration);
        entityManager.flush();
        return registration;
    }

    private GradeItem loadFinal(Setup setup, ExamBoard board, double score, String reason) {
        return gradeItemRecorder.loadProgramEnrollmentGrade(setup.enrollment().getId(), setup.instructor().getId(),
                new CreateGradeItemRequest(GradeItemType.FINAL, 1, null, LocalDateTime.now(), BigDecimal.valueOf(score),
                        null, board.getId(), reason));
    }

    private GradeItem loadExam(Setup setup, double score) {
        return gradeItemRecorder.loadProgramEnrollmentGrade(setup.enrollment().getId(), setup.instructor().getId(),
                new CreateGradeItemRequest(GradeItemType.EXAM, 1, null, LocalDateTime.now(), BigDecimal.valueOf(score),
                        null, null, null));
    }

    private void publishExam(Setup setup) {
        sectionGradeItemPublisher.publishProgramSection(setup.section().getId(), setup.instructor().getId(),
                setup.instructor().getPerson().getId(), new PublishGradeItemRequest(GradeItemType.EXAM, 1));
        entityManager.flush();
    }

    private void correctExam(Setup setup, GradeItem exam, double score) {
        publishedGradeCorrector.correct(exam.getId(),
                new CorrectGradeItemRequest(Grade.of(BigDecimal.valueOf(score)), CORRECTION_REASON, null),
                setup.enrollment().getStudent().getPerson().getId());
        entityManager.flush();
        entityManager.clear();
    }

    private Long approvedSubjectsCertificateOf(Setup setup) {
        Person issuer = setup.instructor().getPerson();
        Long id = rows.validApprovedSubjects(setup.enrollment().getStudent().getId(),
                setup.enrollment().getProgramEnrollment().getId(), issuer.getId());
        entityManager.flush();
        entityManager.clear();
        return id;
    }

    private Setup approvedByFinal(String dni) {
        Setup setup = setup(dni, 1, true, EnrollmentStatus.REGULAR);
        ExamBoard board = board(setup);
        register(board, setup.enrollment());
        loadFinal(setup, board, 7.0, null);
        entityManager.flush();
        assertThat(setup.enrollment().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        return setup;
    }

    @Test
    void correctingAFinalGradeRevokesTheApprovedSubjectsCertificate() {
        Setup setup = approvedByFinal("75000001");
        Long certificateId = approvedSubjectsCertificateOf(setup);
        ExamBoard board = entityManager.createQuery("select b from ExamBoard b", ExamBoard.class).getResultList().getFirst();

        loadFinal(setup, board, 9.0, CORRECTION_REASON);
        entityManager.flush();
        entityManager.clear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void clearingTheFinalGradeRevokesTheApprovedSubjectsCertificate() {
        Setup setup = approvedByFinal("75000002");
        Long certificateId = approvedSubjectsCertificateOf(setup);
        ExamBoard board = entityManager.createQuery("select b from ExamBoard b", ExamBoard.class).getResultList().getFirst();

        gradeItemRecorder.clearProgramEnrollmentFinal(setup.enrollment().getId(), setup.instructor().getId(), board.getId(),
                CORRECTION_REASON);
        entityManager.flush();
        entityManager.clear();

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void correctingAPartialGradeThatKeepsTheOutcomeLeavesTheApprovedSubjectsCertificateValid() {
        Setup setup = setup("75000003", 2, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadExam(setup, 3.0);
        entityManager.flush();
        publishExam(setup);
        Long certificateId = approvedSubjectsCertificateOf(setup);

        correctExam(setup, exam, 5.0);

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("VALID");
    }

    @Test
    void correctingAPartialGradeThatDropsThePromotionRevokesTheApprovedSubjectsCertificate() {
        Setup setup = setup("75000004", 1, false, EnrollmentStatus.IN_PROGRESS);
        GradeItem exam = loadExam(setup, 8.0);
        entityManager.flush();
        publishExam(setup);
        assertThat(setup.enrollment().getStatus()).isEqualTo(EnrollmentStatus.APPROVED);
        Long certificateId = approvedSubjectsCertificateOf(setup);

        correctExam(setup, exam, 2.0);

        assertThat(rows.approvedSubjectsStatus(certificateId)).isEqualTo("REVOKED");
    }

    @Test
    void withdrawingTheProgramEnrollmentWithdrawsItsRegisteredExamBoardRegistrations() {
        Setup setup = setup("75000005", 1, true, EnrollmentStatus.REGULAR);
        ExamBoard board = board(setup);
        ExamBoardRegistration registration = register(board, setup.enrollment());
        Long registrationId = registration.getId();

        programEnrollmentService.withdrawForNonpayment(setup.enrollment().getProgramEnrollment().getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(ExamBoardRegistration.class, registrationId).getStatus())
                .isEqualTo(ExamBoardRegistrationStatus.WITHDRAWN);
    }

    @Test
    void examBoardActaLoadsForTheRemainingStudentsAfterAnotherStudentWasWithdrawn() {
        Setup withdrawn = setup("75000006", 1, true, EnrollmentStatus.REGULAR);
        SubjectSectionEnrollment remaining = secondStudent(withdrawn, "75000061");
        ExamBoard board = board(withdrawn);
        register(board, withdrawn.enrollment());
        ExamBoardRegistration remainingRegistration = register(board, remaining);
        Long remainingEnrollmentId = remaining.getId();

        programEnrollmentService.withdrawForNonpayment(withdrawn.enrollment().getProgramEnrollment().getId());
        entityManager.flush();

        examBoardFinalGradesRecorder.record(board.getId(), withdrawn.instructor().getId(),
                List.of(new ExamBoardFinalGradeEntryRequest(remainingRegistration.getId(), Grade.of(BigDecimal.valueOf(8.0)), null, null)));
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(SubjectSectionEnrollment.class, remainingEnrollmentId).getStatus())
                .isEqualTo(EnrollmentStatus.APPROVED);
    }

    @Test
    void withdrawingTheProgramEnrollmentConsumesTheApprovedLateExam() {
        Setup setup = setup("75000007", 1, true, EnrollmentStatus.REGULAR);
        ProgramEnrollment programEnrollment = setup.enrollment().getProgramEnrollment();
        Person director = setup.instructor().getPerson();
        DirectorApprovalRequest request = DirectorApprovalRequest.open(ExceptionType.LATE_EXAM, programEnrollment,
                setup.section().getSubject(), "Necesita rendir fuera de término", director, LocalDateTime.now());
        request.decide(DirectorApprovalRequestStatus.APPROVED, director, "Aprobado", LocalDateTime.now());
        entityManager.persist(request);
        entityManager.flush();
        Long programEnrollmentId = programEnrollment.getId();
        Long subjectId = setup.section().getSubject().getId();
        assertThat(directorApprovalRequestRepository.hasUnconsumedApproved(programEnrollmentId, subjectId, ExceptionType.LATE_EXAM)).isTrue();

        programEnrollmentService.withdrawForNonpayment(programEnrollmentId);
        entityManager.flush();
        entityManager.clear();

        assertThat(directorApprovalRequestRepository.hasUnconsumedApproved(programEnrollmentId, subjectId, ExceptionType.LATE_EXAM)).isFalse();
    }

    @Test
    void loadingAFinalGradeAfterTheWithdrawalIsRejected() {
        Setup setup = setup("75000008", 1, true, EnrollmentStatus.REGULAR);
        ExamBoard board = board(setup);
        register(board, setup.enrollment());
        programEnrollmentService.withdrawForNonpayment(setup.enrollment().getProgramEnrollment().getId());
        entityManager.flush();

        assertThatThrownBy(() -> loadFinal(setup, board, 7.0, null)).isInstanceOf(ProgramEnrollmentRequiredException.class);
    }
}
