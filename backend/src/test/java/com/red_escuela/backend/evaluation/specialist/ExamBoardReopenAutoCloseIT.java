package com.red_escuela.backend.evaluation.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.api.EndedSectionFinder;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.evaluation.api.ClosureNotificationTargetsFinder;
import com.red_escuela.backend.evaluation.entity.ExamBoard;
import com.red_escuela.backend.evaluation.entity.ExamBoardAuditAction;
import com.red_escuela.backend.evaluation.entity.ExamBoardInstructor;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistration;
import com.red_escuela.backend.evaluation.entity.ExamBoardRegistrationStatus;
import com.red_escuela.backend.evaluation.entity.ExamBoardStatus;
import com.red_escuela.backend.evaluation.repository.CommissionClosureReminderRepository;
import com.red_escuela.backend.evaluation.repository.CommissionClosureRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardAuditRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardInstructorRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRegistrationRepository;
import com.red_escuela.backend.evaluation.repository.ExamBoardRepository;
import com.red_escuela.backend.evaluation.repository.SectionGradeItemPublicationRepository;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.StatusType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExamBoardReopenAutoCloseIT extends AbstractIntegrationTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2020-06-20T12:00:00Z"));
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ExamBoardRepository examBoardRepository;
    @Autowired
    private ExamBoardAuditRepository auditRepository;
    @Autowired
    private ExamBoardRegistrationRepository registrationRepository;
    @Autowired
    private ExamBoardInstructorRepository instructorRepository;
    @Autowired
    private CommissionClosureReminderRepository reminderRepository;
    @Autowired
    private CommissionClosureRepository closureRepository;
    @Autowired
    private SectionGradeItemPublicationRepository publicationRepository;
    @Autowired
    private ExamBoardManager examBoardManager;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private ExamBoardReopener reopener;
    private TermEndEscalationJob job;
    private ExamBoard board;
    private ExamBoardRegistration registration;

    @BeforeEach
    void seedClosedBoardAndReopenIt() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Term term = fixtures.term(2170);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        Program program = fixtures.program();
        ProgramEnrollment programEnrollment = new ProgramEnrollment();
        programEnrollment.setStudent(student);
        programEnrollment.setProgram(program);
        programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        programEnrollment.setEnrollmentDate(LocalDateTime.of(2170, 3, 1, 9, 0));
        entityManager.persist(programEnrollment);
        Subject subject = fixtures.subject();
        var cycle = fixtures.programCycle(program, term, AcademicOfferingCycleStatus.APPROVED);
        SubjectSection section = fixtures.section(subject, term, StatusType.ACTIVE, cycle);
        SubjectSectionEnrollment enrollment = new SubjectSectionEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgramEnrollment(programEnrollment);
        enrollment.setSubjectSection(section);
        enrollment.setStatus(EnrollmentStatus.IN_PROGRESS);
        enrollment.setEnrolledAt(LocalDateTime.of(2170, 3, 1, 9, 0));
        enrollment.setAttemptNumber(1);
        entityManager.persist(enrollment);

        board = new ExamBoard();
        board.setTerm(term);
        board.setSubject(subject);
        board.setExamDate(LocalDateTime.of(2020, 6, 1, 10, 0));
        board.setStatus(ExamBoardStatus.CLOSED);
        entityManager.persist(board);
        ExamBoardInstructor tribunal = new ExamBoardInstructor();
        tribunal.setExamBoard(board);
        tribunal.setInstructor(fixtures.instructor());
        entityManager.persist(tribunal);
        registration = new ExamBoardRegistration();
        registration.setExamBoard(board);
        registration.setProgramSubjectSectionEnrollment(enrollment);
        registration.setStatus(ExamBoardRegistrationStatus.REGISTERED);
        registration.setRegisteredAt(LocalDateTime.of(2170, 5, 20, 10, 0));
        entityManager.persist(registration);
        entityManager.flush();

        CurrentActorFinder actorFinder = mock(CurrentActorFinder.class);
        when(actorFinder.currentPersonId()).thenReturn(fixtures.person().getId());
        reopener = new ExamBoardReopener(examBoardRepository, auditRepository, actorFinder, clock);
        TermEndClosureReview review = new TermEndClosureReview(mock(EndedSectionFinder.class), closureRepository, publicationRepository,
                mock(ClosureNotificationTargetsFinder.class), examBoardRepository, registrationRepository, auditRepository, instructorRepository);
        job = new TermEndEscalationJob(review, reminderRepository, examBoardManager, registrationRepository, examBoardRepository,
                instructorRepository, eventPublisher, TransactionOperations.withoutTransaction(), clock);

        reopener.reopen(board.getId(), "Error de carga en el acta");
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void aReopenedBoardStaysScheduledWhileTheGraceWindowFromTheReopeningIsOpen() {
        now.set(Instant.parse("2020-07-01T12:00:00Z"));

        assertThat(auditRepository.findLastOccurredAt(board.getId(), ExamBoardAuditAction.REOPENED))
                .contains(LocalDateTime.of(2020, 6, 20, 12, 0));
        assertThat(job.closeExpiredExamBoards()).isZero();

        entityManager.flush();
        entityManager.clear();
        assertThat(examBoardRepository.findById(board.getId()).orElseThrow().getStatus()).isEqualTo(ExamBoardStatus.SCHEDULED);
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamBoardRegistrationStatus.REGISTERED);
    }

    @Test
    void aReopenedBoardIsClosedAndUngradedRegistrationsAreMarkedAbsentOnceTheGraceFromTheReopeningHasPassed() {
        now.set(Instant.parse("2020-07-05T12:00:00Z"));

        assertThat(job.closeExpiredExamBoards()).isEqualTo(1);

        entityManager.flush();
        entityManager.clear();
        assertThat(examBoardRepository.findById(board.getId()).orElseThrow().getStatus()).isEqualTo(ExamBoardStatus.CLOSED);
        assertThat(registrationRepository.findById(registration.getId()).orElseThrow().getStatus())
                .isEqualTo(ExamBoardRegistrationStatus.ABSENT);
    }
}
