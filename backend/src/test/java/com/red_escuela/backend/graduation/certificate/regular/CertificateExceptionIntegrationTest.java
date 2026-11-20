package com.red_escuela.backend.graduation.certificate.regular;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionApproval;
import com.red_escuela.backend.graduation.certificate.regular.entity.CertificateExceptionStatus;
import com.red_escuela.backend.graduation.certificate.regular.entity.OrganismKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularRequestOutcome;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.entity.ValidityDays;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionNotApprovedException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionConsumer;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionExpirer;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CertificateExceptionIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);
    private static final String REASON = "El alumno presentó un reclamo formal ante Defensa del Consumidor";

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private CertificateExceptionApprovalRepository repository;
    @Autowired
    private RegularCertificateRequestRepository requestRepository;
    @Autowired
    private CertificateExceptionConsumer consumer;
    @Autowired
    private CertificateExceptionExpirer expirer;

    private Long enrollmentId;
    private Long studentId;
    private Long requesterId;
    private Long directorId;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(NOW);
        transactions.executeWithoutResult(status -> {
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(persistPerson());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(new CycleTestFixtures(entityManager).program());
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(NOW.minusMonths(8));
            entityManager.persist(enrollment);
            requesterId = persistPerson().getId();
            directorId = persistPerson().getId();
            entityManager.flush();
            enrollmentId = enrollment.getId();
            studentId = student.getId();
        });
    }

    private Person persistPerson() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("exception." + suffix);
        person.setEmail("exception." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private Long persistApproved(LocalDateTime approvedAt) {
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(RegularityKind.PROGRAM, enrollmentId, studentId,
                OrganismKind.EDUCATION_MINISTRY, "EXP-77/2026", REASON, requesterId, approvedAt.minusHours(1));
        approval.approve(directorId, "Reclamo verificado con el expediente", new ValidityDays(7), approvedAt);
        return repository.saveAndFlush(approval).getId();
    }

    private Long persistCertificate(String code) {
        RegularCertificateRequest request = requestRepository.saveAndFlush(RegularCertificateRequest.record(RegularityKind.PROGRAM, enrollmentId,
                studentId, requesterId, NOW, RegularCertificateChannel.STAFF, RegularRequestOutcome.ISSUED, List.of()));
        jdbc.update("insert into regular_student_certificate (code, kind, program_enrollment_id, student_id, status, issued_at, expires_at, "
                + "validity_days, issued_by_id, issue_channel, request_id, billing_mode_at_issue, standing_basis, snapshot, template_ref, "
                + "pdf_sha256, pdf_path) values (?, 'PROGRAM', ?, ?, 'EXPIRED', now(), now(), 30, ?, 'STAFF', ?, 'OFF', 'EXCEPTION_APPROVED', "
                + "'{}'::jsonb, 'REGULAR_BUILT_IN_V1', repeat('a', 64), 'x.pdf')", code, enrollmentId, studentId, requesterId, request.getId());
        return jdbc.queryForObject("select id from regular_student_certificate where code = ?", Long.class, code);
    }

    private String uniqueCode() {
        String alphabet = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
        StringBuilder code = new StringBuilder();
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        for (int index = 0; index < 26; index++) {
            code.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return code.toString();
    }

    @Test
    void approvedExceptionIsPersistedWithItsHistory() {
        Long id = persistApproved(NOW);

        CertificateExceptionApproval reloaded = transactions.execute(status -> {
            CertificateExceptionApproval found = repository.findById(id).orElseThrow();
            found.getStatusChanges().size();
            return found;
        });

        assertThat(reloaded.getStatus()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(reloaded.getValidUntil()).isEqualTo(NOW.plusDays(7));
        assertThat(reloaded.getStatusChanges()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from certificate_exception_status_change where approval_id = ?", Integer.class, id))
                .isEqualTo(2);
    }

    @Test
    void databaseRejectsADeciderEqualToTheRequester() {
        Long id = persistApproved(NOW);

        assertThatThrownBy(() -> jdbc.update("update certificate_exception_approval set decided_by_id = requested_by_id where id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void consumeMarksTheExceptionConsumedInTheDatabase() {
        Long id = persistApproved(NOW);
        Long certificateId = persistCertificate(uniqueCode());

        consumer.consume(id, certificateId, requesterId);

        assertThat(jdbc.queryForObject("select status from certificate_exception_approval where id = ?", String.class, id)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("select consumed_certificate_id from certificate_exception_approval where id = ?", Long.class, id))
                .isEqualTo(certificateId);
        assertThatThrownBy(() -> consumer.consume(id, certificateId, requesterId)).isInstanceOf(ExceptionAlreadyConsumedException.class);
    }

    @Test
    void twoConcurrentConsumptionsLetExactlyOneWin() throws Exception {
        Long id = persistApproved(NOW);
        Long firstCertificate = persistCertificate(uniqueCode());
        Long secondCertificate = persistCertificate(uniqueCode());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (Long certificateId : List.of(firstCertificate, secondCertificate)) {
                Callable<Throwable> attempt = () -> {
                    ready.countDown();
                    go.await();
                    try {
                        consumer.consume(id, certificateId, requesterId);
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    }
                };
                futures.add(executor.submit(attempt));
            }
            ready.await();
            go.countDown();
            List<Throwable> outcomes = new ArrayList<>();
            for (Future<Throwable> future : futures) {
                outcomes.add(future.get());
            }

            assertThat(outcomes.stream().filter(outcome -> outcome == null)).hasSize(1);
            assertThat(outcomes.stream().filter(outcome -> outcome instanceof ExceptionAlreadyConsumedException)).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from certificate_exception_status_change where approval_id = ? and event = 'CONSUME'",
                    Integer.class, id)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void databaseRefusesTwoCertificatesLinkedToTheSameException() {
        Long id = persistApproved(NOW);
        Long first = persistCertificate(uniqueCode());
        Long second = persistCertificate(uniqueCode());
        jdbc.update("update regular_student_certificate set exception_id = ? where id = ?", id, first);

        assertThatThrownBy(() -> jdbc.update("update regular_student_certificate set exception_id = ? where id = ?", id, second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void expirerOnlyExpiresApprovedExceptionsPastTheirValidityUsingTheClock() {
        Long due = persistApproved(NOW.minusDays(8));
        Long stillValid = persistApproved(NOW.minusDays(2));
        Long expiredAtBoundary = persistApproved(NOW.minusDays(7));

        int expired = expirer.expireDue();

        assertThat(expired).isEqualTo(2);
        assertThat(statusOf(due)).isEqualTo("EXPIRED");
        assertThat(statusOf(expiredAtBoundary)).isEqualTo("EXPIRED");
        assertThat(statusOf(stillValid)).isEqualTo("APPROVED");
        assertThatThrownBy(() -> consumer.consume(due, persistCertificate(uniqueCode()), requesterId)).isInstanceOf(ExceptionNotApprovedException.class);

        MutableClock.INSTANCE.setDateTime(NOW.plusDays(6));
        assertThat(expirer.expireDue()).isEqualTo(1);
        assertThat(statusOf(stillValid)).isEqualTo("EXPIRED");
    }

    @Test
    void consumeIsRejectedOnceTheClockPassesValidUntilEvenBeforeTheJobRuns() {
        Long id = persistApproved(NOW);
        Long certificateId = persistCertificate(uniqueCode());
        MutableClock.INSTANCE.setDateTime(NOW.plusDays(7));

        assertThatThrownBy(() -> consumer.consume(id, certificateId, requesterId)).isInstanceOf(ExceptionNotApprovedException.class);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject("select status from certificate_exception_approval where id = ?", String.class, id);
    }
}
