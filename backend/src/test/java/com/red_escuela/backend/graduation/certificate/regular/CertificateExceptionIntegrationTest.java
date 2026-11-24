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
import com.red_escuela.backend.graduation.certificate.regular.dto.CertificateExceptionResponse;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionAlreadyOpenException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionAlreadyConsumedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionTargetMismatchException;
import com.red_escuela.backend.graduation.certificate.regular.exception.CertificateExceptionNotFoundException;
import com.red_escuela.backend.graduation.certificate.regular.exception.OwnExceptionDecisionException;
import com.red_escuela.backend.graduation.certificate.regular.exception.ExceptionNotApprovedException;
import com.red_escuela.backend.graduation.certificate.regular.repository.CertificateExceptionApprovalRepository;
import com.red_escuela.backend.graduation.certificate.regular.repository.RegularCertificateRequestRepository;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionConsumer;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionReader;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionDueExpirer;
import com.red_escuela.backend.graduation.certificate.regular.dto.RequestCertificateExceptionRequest;
import com.red_escuela.backend.graduation.certificate.regular.usecase.ApproveCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RejectCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.RequestCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.usecase.WithdrawCertificateExceptionUseCase;
import com.red_escuela.backend.graduation.certificate.regular.specialist.CertificateExceptionExpirer;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.platform.infrastructure.FeatureSnapshotHolder;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private FeatureSnapshotHolder snapshotHolder;
    @Autowired
    private CertificateExceptionApprovalRepository repository;
    @Autowired
    private RegularCertificateRequestRepository requestRepository;
    @Autowired
    private CertificateExceptionConsumer consumer;
    @Autowired
    private CertificateExceptionExpirer expirer;

    @Autowired
    private CertificateExceptionDueExpirer dueExpirer;
    @Autowired
    private RequestCertificateExceptionUseCase requestUseCase;
    @Autowired
    private CertificateExceptionReader reader;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private ApproveCertificateExceptionUseCase approveUseCase;
    @Autowired
    private RejectCertificateExceptionUseCase rejectUseCase;
    @Autowired
    private WithdrawCertificateExceptionUseCase withdrawUseCase;

    private record Target(Long studentId, Long enrollmentId) {
    }

    private Institution institution;
    private Target target;
    private Long enrollmentId;
    private Long studentId;
    private Long requesterId;
    private Long directorId;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(NOW);
        setBillingMode("OFF");
        transactions.executeWithoutResult(status -> {
            institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            requesterId = persistPerson().getId();
            directorId = persistPerson().getId();
        });
        target = newTarget();
        enrollmentId = target.enrollmentId();
        studentId = target.studentId();
    }

    @AfterEach
    void restore() {
        SecurityContextHolder.clearContext();
        jdbc.update("update regular_certificate_settings set exception_validity_days = 7");
        setBillingMode("FULL");
    }

    private void setBillingMode(String mode) {
        jdbc.update("update platform_feature set enabled = ?, mode = ? where code = 'BILLING'", !"OFF".equals(mode), mode);
        snapshotHolder.refresh();
    }

    private void actAs(Long personId) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(personId.toString(), null));
    }

    private Target newTarget() {
        return transactions.execute(status -> {
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
            entityManager.flush();
            return new Target(student.getId(), enrollment.getId());
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
        return persistApproved(approvedAt, target);
    }

    private Long persistApproved(LocalDateTime approvedAt, Target owner) {
        CertificateExceptionApproval approval = CertificateExceptionApproval.request(RegularityKind.PROGRAM, owner.enrollmentId(), owner.studentId(),
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

    private String fullNameOf(Long personId) {
        return jdbc.queryForObject("select name || ' ' || last_name from person where id = ?", String.class, personId);
    }

    @Test
    void getResolvesStudentProgramRequesterAndDeciderNames() {
        Long id = persistApproved(NOW);

        CertificateExceptionResponse response = reader.get(id, true);

        assertThat(response.studentName()).isEqualTo(jdbc.queryForObject(
                "select p.name || ' ' || p.last_name from student s join person p on p.id = s.person_id where s.id = ?", String.class, studentId));
        assertThat(response.programName()).isEqualTo(jdbc.queryForObject(
                "select pr.name from program_enrollment pe join program pr on pr.id = pe.program_id where pe.id = ?", String.class, enrollmentId));
        assertThat(response.requestedByName()).isEqualTo(fullNameOf(requesterId));
        assertThat(response.decidedByName()).isEqualTo(fullNameOf(directorId));
    }

    @Test
    void pendingExceptionHasNoDeciderName() {
        actAs(requesterId);
        Long id = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId, OrganismKind.COURT,
                "Causa 1/2026", REASON)).id();

        CertificateExceptionResponse response = reader.get(id, true);

        assertThat(response.decidedById()).isNull();
        assertThat(response.decidedByName()).isNull();
        assertThat(response.requestedByName()).isEqualTo(fullNameOf(requesterId));
    }

    @Test
    void listResolvesNamesInTheSameNumberOfQueriesRegardlessOfSize() {
        persistApproved(NOW);
        Long firstCount = queryCountOfListing();
        persistApproved(NOW, newTarget());
        persistApproved(NOW, newTarget());
        persistApproved(NOW, newTarget());

        Long largerCount = queryCountOfListing();

        assertThat(largerCount).isEqualTo(firstCount);
    }

    private Long queryCountOfListing() {
        org.hibernate.stat.Statistics statistics = entityManagerFactory.unwrap(org.hibernate.SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        List<CertificateExceptionResponse> listed = reader.list(true, null, null);
        long count = statistics.getPrepareStatementCount();
        statistics.setStatisticsEnabled(false);
        assertThat(listed).allSatisfy(response -> {
            assertThat(response.studentName()).isNotBlank();
            assertThat(response.programName()).isNotBlank();
            assertThat(response.requestedByName()).isNotBlank();
            assertThat(response.decidedById() == null ? response.decidedByName() == null : !response.decidedByName().isBlank()).isTrue();
        });
        return count;
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
    void consumeMarksTheExceptionConsumedInTheDatabase() {
        Long id = persistApproved(NOW);
        Long certificateId = persistCertificate(uniqueCode());

        consumer.consume(id, certificateId, studentId, RegularityKind.PROGRAM, enrollmentId, requesterId);

        assertThat(jdbc.queryForObject("select status from certificate_exception_approval where id = ?", String.class, id)).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("select consumed_certificate_id from certificate_exception_approval where id = ?", Long.class, id))
                .isEqualTo(certificateId);
        assertThatThrownBy(() -> consumer.consume(id, certificateId, studentId, RegularityKind.PROGRAM, enrollmentId, requesterId)).isInstanceOf(ExceptionAlreadyConsumedException.class);
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
                        consumer.consume(id, certificateId, studentId, RegularityKind.PROGRAM, enrollmentId, requesterId);
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
        Long due = persistApproved(NOW.minusDays(8), target);
        Long stillValid = persistApproved(NOW.minusDays(2), newTarget());
        Long expiredAtBoundary = persistApproved(NOW.minusDays(7), newTarget());

        int expired = expirer.expireDue();

        assertThat(expired).isEqualTo(2);
        assertThat(statusOf(due)).isEqualTo("EXPIRED");
        assertThat(statusOf(expiredAtBoundary)).isEqualTo("EXPIRED");
        assertThat(statusOf(stillValid)).isEqualTo("APPROVED");
        assertThatThrownBy(() -> consumer.consume(due, persistCertificate(uniqueCode()), studentId, RegularityKind.PROGRAM, enrollmentId, requesterId)).isInstanceOf(ExceptionNotApprovedException.class);

        MutableClock.INSTANCE.setDateTime(NOW.plusDays(6));
        assertThat(expirer.expireDue()).isEqualTo(1);
        assertThat(statusOf(stillValid)).isEqualTo("EXPIRED");
    }

    @Test
    void consumeIsRejectedOnceTheClockPassesValidUntilEvenBeforeTheJobRuns() {
        Long id = persistApproved(NOW);
        Long certificateId = persistCertificate(uniqueCode());
        MutableClock.INSTANCE.setDateTime(NOW.plusDays(7));

        assertThatThrownBy(() -> consumer.consume(id, certificateId, studentId, RegularityKind.PROGRAM, enrollmentId, requesterId)).isInstanceOf(ExceptionNotApprovedException.class);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    @Test
    void expiringOneDueExceptionNeverTouchesAnAlreadyConsumedOne() {
        Long consumed = persistApproved(NOW.minusDays(7));
        Long certificateId = persistCertificate(uniqueCode());
        MutableClock.INSTANCE.setDateTime(NOW.minusDays(1));
        consumer.consume(consumed, certificateId, studentId, RegularityKind.PROGRAM, enrollmentId, requesterId);
        Long due = persistApproved(NOW.minusDays(8), newTarget());
        MutableClock.INSTANCE.setDateTime(NOW);

        assertThat(dueExpirer.expireIfDue(consumed)).isFalse();
        assertThat(expirer.expireDue()).isEqualTo(1);
        assertThat(statusOf(consumed)).isEqualTo("CONSUMED");
        assertThat(statusOf(due)).isEqualTo("EXPIRED");
    }

    @Test
    void consumeRejectsAnotherStudentOrEnrollment() {
        Long id = persistApproved(NOW);
        Long certificateId = persistCertificate(uniqueCode());
        Target other = newTarget();

        assertThatThrownBy(() -> consumer.consume(id, certificateId, other.studentId(), RegularityKind.PROGRAM, enrollmentId, requesterId))
                .isInstanceOf(ExceptionTargetMismatchException.class);
        assertThatThrownBy(() -> consumer.consume(id, certificateId, studentId, RegularityKind.PROGRAM, other.enrollmentId(), requesterId))
                .isInstanceOf(ExceptionTargetMismatchException.class);
        assertThat(statusOf(id)).isEqualTo("APPROVED");
    }

    @Test
    void databaseRefusesAnExceptionIdThatDoesNotExist() {
        Long certificateId = persistCertificate(uniqueCode());

        assertThatThrownBy(() -> jdbc.update("update regular_student_certificate set exception_id = 987654321 where id = ?", certificateId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvalUsesTheConfiguredExceptionValidityAndTheCurrentActor() {
        jdbc.update("update regular_certificate_settings set exception_validity_days = 10");
        actAs(requesterId);
        CertificateExceptionResponse requested = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.COURT, "Causa 55/2026", REASON));
        actAs(directorId);

        CertificateExceptionResponse approved = approveUseCase.approve(requested.id(), "Reclamo verificado en el juzgado");

        assertThat(requested.status()).isEqualTo(CertificateExceptionStatus.PENDING);
        assertThat(requested.requestedById()).isEqualTo(requesterId);
        assertThat(requested.studentId()).isEqualTo(studentId);
        assertThat(approved.status()).isEqualTo(CertificateExceptionStatus.APPROVED);
        assertThat(approved.decidedById()).isEqualTo(directorId);
        assertThat(approved.validUntil()).isEqualTo(approved.decidedAt().plusDays(10)).isEqualTo(NOW.plusDays(10));
    }

    @Test
    void requesterCannotApproveTheirOwnRequestAndNobodyElseCanWithdrawIt() {
        actAs(requesterId);
        CertificateExceptionResponse requested = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.CONSUMER_DEFENSE, "EXP-1/2026", REASON));

        assertThatThrownBy(() -> approveUseCase.approve(requested.id(), "Reclamo verificado en el juzgado"))
                .isInstanceOf(OwnExceptionDecisionException.class);
        assertThatThrownBy(() -> rejectUseCase.reject(requested.id(), "Reclamo verificado en el juzgado"))
                .isInstanceOf(OwnExceptionDecisionException.class);
        actAs(directorId);
        assertThatThrownBy(() -> withdrawUseCase.withdraw(requested.id())).isInstanceOf(CertificateExceptionNotFoundException.class);
        assertThat(statusOf(requested.id())).isEqualTo("PENDING");
        actAs(requesterId);
        assertThat(withdrawUseCase.withdraw(requested.id()).status()).isEqualTo(CertificateExceptionStatus.WITHDRAWN);
    }

    @Test
    void directorRejectionRecordsTheDirectorAsDecider() {
        actAs(requesterId);
        CertificateExceptionResponse requested = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.OTHER, "Nota 12", REASON));
        actAs(directorId);

        CertificateExceptionResponse rejected = rejectUseCase.reject(requested.id(), "El reclamo no está respaldado");

        assertThat(rejected.status()).isEqualTo(CertificateExceptionStatus.REJECTED);
        assertThat(rejected.decidedById()).isEqualTo(directorId);
    }

    @Test
    void secondOpenRequestForTheSameEnrollmentIsRejectedUntilTheFirstIsClosed() {
        actAs(requesterId);
        CertificateExceptionResponse first = requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.COURT, "Causa 1", REASON));

        assertThatThrownBy(() -> requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId,
                OrganismKind.COURT, "Causa 2", REASON))).isInstanceOf(CertificateExceptionAlreadyOpenException.class);
        withdrawUseCase.withdraw(first.id());
        assertThat(requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId, OrganismKind.COURT,
                "Causa 2", REASON)).status()).isEqualTo(CertificateExceptionStatus.PENDING);
    }

    @Test
    void twoConcurrentRequestsForTheSameEnrollmentLetExactlyOneWin() throws Exception {
        int threads = 2;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Throwable>> futures = new ArrayList<>();
            for (int index = 0; index < threads; index++) {
                Callable<Throwable> attempt = () -> {
                    actAs(requesterId);
                    ready.countDown();
                    go.await();
                    try {
                        requestUseCase.request(new RequestCertificateExceptionRequest(RegularityKind.PROGRAM, enrollmentId, OrganismKind.COURT,
                                "Causa concurrente", REASON));
                        return null;
                    } catch (Throwable failure) {
                        return failure;
                    } finally {
                        SecurityContextHolder.clearContext();
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
            assertThat(outcomes.stream().filter(outcome -> outcome instanceof CertificateExceptionAlreadyOpenException)).hasSize(1);
            assertThat(jdbc.queryForObject("select count(*) from certificate_exception_approval where program_enrollment_id = ?", Integer.class,
                    enrollmentId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private String statusOf(Long id) {
        return jdbc.queryForObject("select status from certificate_exception_approval where id = ?", String.class, id);
    }
}
