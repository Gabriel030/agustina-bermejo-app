package com.red_escuela.backend.graduation.specialist;

import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.academicstructure.entity.SubjectSection;
import com.red_escuela.backend.academicstructure.entity.Term;
import com.red_escuela.backend.config.TestSubjectCodes;
import com.red_escuela.backend.enrollment.entity.EnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectSectionEnrollment;
import com.red_escuela.backend.billing.exception.PendingDebtException;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.support.BillingTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.events.GraduationRequestApprovedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestRejectedEvent;
import com.red_escuela.backend.graduation.events.GraduationRequestVerifiedEvent;
import com.red_escuela.backend.graduation.usecase.request.ApproveGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.request.RejectGraduationRequestCommand;
import com.red_escuela.backend.graduation.usecase.request.RejectGraduationRequestUseCase;
import com.red_escuela.backend.graduation.usecase.request.VerifyGraduationRequestDebtUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.testsupport.FailingEventListenersConfiguration;
import com.red_escuela.backend.testsupport.FailingEventListenersConfiguration.FailingGraduationListener;
import com.red_escuela.backend.testsupport.TermFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.UUID;

import static com.red_escuela.backend.shared.domain.TestMoney.ars;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@ActiveProfiles("test")
@Import(FailingEventListenersConfiguration.class)
class GraduationTransactionBoundaryIT extends AbstractIntegrationTest {

    @Autowired
    private ApproveGraduationRequestUseCase approveGraduationRequest;

    @Autowired
    private RejectGraduationRequestUseCase rejectGraduationRequest;

    @Autowired
    private VerifyGraduationRequestDebtUseCase verifyGraduationRequestDebt;

    @Autowired
    private FailingGraduationListener failingListener;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Person studentPerson;
    private ProgramEnrollment programEnrollment;
    private Long requestId;

    @BeforeEach
    void seedAnAdminAndAStudentWithAProgramEnrollment() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 12);
            Institution institution = new Institution();
            institution.setName("Instituto " + suffix);
            entityManager.persist(institution);

            studentPerson = persistPerson("student" + suffix);
            Student student = new Student();
            student.setPerson(studentPerson);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);

            Program program = new Program();
            program.setName("Carrera " + suffix);
            program.setTotalYears(3);
            program.setTotalCreditsRequired(10);
            entityManager.persist(program);

            programEnrollment = new ProgramEnrollment();
            programEnrollment.setStudent(student);
            programEnrollment.setProgram(program);
            programEnrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            programEnrollment.setEnrollmentDate(LocalDateTime.now());
            entityManager.persist(programEnrollment);

            Subject subject = new Subject();
            subject.setCode(TestSubjectCodes.next());
            subject.setName("Materia " + suffix);
            entityManager.persist(subject);
            ProgramSubject programSubject = new ProgramSubject();
            programSubject.setProgram(program);
            programSubject.setSubject(subject);
            programSubject.setCredits(10);
            programSubject.setStatus(StatusType.ACTIVE);
            entityManager.persist(programSubject);
            Term term = TermFixtures.newTerm();
            term.setYear(2026);
            term.setDescription("2026-1C");
            term.setStatus(StatusType.ACTIVE);
            entityManager.persist(term);
            SubjectSection section = new SubjectSection();
            section.setName("Comision " + suffix);
            section.setSubject(subject);
            section.setTerm(term);
            section.setStatus(StatusType.ACTIVE);
            entityManager.persist(section);
            SubjectSectionEnrollment passed = new SubjectSectionEnrollment();
            passed.setStudent(student);
            passed.setProgramEnrollment(programEnrollment);
            passed.setSubjectSection(section);
            passed.setStatus(EnrollmentStatus.APPROVED);
            passed.setEnrolledAt(LocalDateTime.now());
            passed.setAttemptNumber(1);
            entityManager.persist(passed);

            Person admin = persistPerson("admin" + suffix);
            SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(admin.getId().toString(), null));
        });
    }

    @AfterEach
    void cleanUp() {
        failingListener.stopFailing();
        SecurityContextHolder.clearContext();
    }

    private Person persistPerson(String key) {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        person.setName("Persona");
        person.setLastname(key);
        person.setDni(key);
        person.setUsername(key);
        person.setEmail(key + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        return person;
    }

    private void seedRequestAt(GraduationRequestStatus status) {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            GraduationRequest request = new GraduationRequest();
            request.setProgramEnrollment(programEnrollment);
            request.setStatus(status);
            request.setRequestedAt(LocalDateTime.now());
            entityManager.persist(request);
            requestId = request.getId();
        });
    }

    private void seedTwoOverdueInstallments() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            BillingTestFixtures.invoice().forProgramEnrollment(programEnrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 3)).dueOn(YearMonth.of(2026, 3).atDay(10)).withBaseAmount(ars(15000)).build();
            BillingTestFixtures.invoice().forProgramEnrollment(programEnrollment).ofType(FeeType.MONTHLY_INSTALLMENT)
                    .forPeriod(YearMonth.of(2026, 4)).dueOn(YearMonth.of(2026, 4).atDay(10)).withBaseAmount(ars(18000)).build();
        });
    }

    private String requestStatus() {
        return jdbcTemplate.queryForObject("select status from graduation_request where id = ?", String.class, requestId);
    }

    private String programEnrollmentStatus() {
        return jdbcTemplate.queryForObject("select status from program_enrollment where id = ?", String.class, programEnrollment.getId());
    }

    private long debtNotifications() {
        return jdbcTemplate.queryForObject("select count(*) from notification where related_entity_id = ? and type = 'GRADUATION_REQUEST_DEBT_BLOCKED'",
                Long.class, requestId);
    }

    @Test
    void approvingFailingWhilePublishingTheEventKeepsTheRequestAwaitingTheDirectorAndTheEnrollmentActive() {
        seedRequestAt(GraduationRequestStatus.ADMIN_VERIFIED);
        failingListener.failOn(GraduationRequestApprovedEvent.class);

        assertThatThrownBy(() -> approveGraduationRequest.approve(requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(requestStatus()).isEqualTo(GraduationRequestStatus.ADMIN_VERIFIED.name());
        assertThat(programEnrollmentStatus()).isEqualTo(ProgramEnrollmentStatus.ENROLLED.name());
    }

    @Test
    void approvingWithoutFailuresCompletesTheRequestAndTheEnrollment() {
        seedRequestAt(GraduationRequestStatus.ADMIN_VERIFIED);

        approveGraduationRequest.approve(requestId);

        assertThat(requestStatus()).isEqualTo(GraduationRequestStatus.APPROVED.name());
        assertThat(programEnrollmentStatus()).isNotEqualTo(ProgramEnrollmentStatus.ENROLLED.name());
    }

    @Test
    void rejectingFailingWhilePublishingTheEventLeavesTheRequestWithoutRejection() {
        seedRequestAt(GraduationRequestStatus.REQUESTED);
        failingListener.failOn(GraduationRequestRejectedEvent.class);

        assertThatThrownBy(() -> rejectGraduationRequest.reject(new RejectGraduationRequestCommand(requestId, "Falta documentación")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(requestStatus()).isEqualTo(GraduationRequestStatus.REQUESTED.name());
        assertThat(jdbcTemplate.queryForObject("select rejection_reason from graduation_request where id = ?", String.class, requestId)).isNull();
    }

    @Test
    void verifyingFailingWhilePublishingTheEventLeavesTheRequestUnverified() {
        seedRequestAt(GraduationRequestStatus.REQUESTED);
        failingListener.failOn(GraduationRequestVerifiedEvent.class);

        assertThatThrownBy(() -> verifyGraduationRequestDebt.verify(requestId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("listener failed");

        assertThat(requestStatus()).isEqualTo(GraduationRequestStatus.REQUESTED.name());
        assertThat(jdbcTemplate.queryForObject("select verified_at from graduation_request where id = ?", Object.class, requestId)).isNull();
    }

    @Test
    void verifyingWithDebtRollsBackTheRequestButTheDebtBlockedNotificationSurvives() {
        seedRequestAt(GraduationRequestStatus.REQUESTED);
        seedTwoOverdueInstallments();

        assertThatThrownBy(() -> verifyGraduationRequestDebt.verify(requestId)).isInstanceOf(PendingDebtException.class);

        assertThat(requestStatus()).isEqualTo(GraduationRequestStatus.REQUESTED.name());
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(debtNotifications()).isEqualTo(1L));
    }
}
