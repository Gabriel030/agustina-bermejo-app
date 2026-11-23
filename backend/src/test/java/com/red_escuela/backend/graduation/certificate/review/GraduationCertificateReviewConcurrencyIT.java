package com.red_escuela.backend.graduation.certificate.review;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.review.dto.CertificateReviewDecision;
import com.red_escuela.backend.graduation.certificate.review.specialist.GraduationCertificateReviewDecider;
import com.red_escuela.backend.graduation.certificate.usecase.IssueGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.entity.CertificateReviewStatus;
import com.red_escuela.backend.graduation.entity.GraduationRequest;
import com.red_escuela.backend.graduation.entity.GraduationRequestStatus;
import com.red_escuela.backend.graduation.exception.GraduationCertificateReviewRequiredException;
import com.red_escuela.backend.graduation.exception.InvalidGraduationCertificateReviewDecisionException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GraduationCertificateReviewConcurrencyIT extends AbstractIntegrationTest {

    private static final long BLOCKED_WINDOW_MILLIS = 1500;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IssueGraduationCertificateUseCase issueUseCase;
    @Autowired
    private GraduationCertificateReviewDecider decider;
    @Autowired
    private JdbcTemplate jdbc;

    private Long enrollmentId;
    private Long graduationRequestId;
    private Long staffId;
    private ExecutorService executor;

    @BeforeEach
    void seedEnabledGraduation() {
        executor = Executors.newFixedThreadPool(3);
        transactionTemplate.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Student student = new Student();
            student.setPerson(fixtures.person());
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(fixtures.program());
            enrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
            enrollment.setEnrollmentDate(LocalDateTime.now().minusYears(3));
            enrollment.setCompletionDate(LocalDateTime.of(2026, 7, 15, 10, 0));
            entityManager.persist(enrollment);
            GraduationRequest request = new GraduationRequest();
            request.setProgramEnrollment(enrollment);
            request.setStatus(GraduationRequestStatus.APPROVED);
            request.setRequestedAt(LocalDateTime.now());
            request.setCertificateReviewStatus(CertificateReviewStatus.ENABLED);
            entityManager.persist(request);
            Person staff = fixtures.person();
            entityManager.flush();
            enrollmentId = enrollment.getId();
            graduationRequestId = request.getId();
            staffId = staff.getId();
        });
    }

    @AfterEach
    void stopExecutor() {
        executor.shutdownNow();
    }

    private <T> T asStaff(Callable<T> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staffId.toString(), null));
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Future<?> holdRowLockUntil(CountDownLatch locked, CountDownLatch release, String statementAfterRelease) {
        return executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbc.queryForList("select id from graduation_request where id = ? for update", graduationRequestId);
            locked.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (statementAfterRelease != null) {
                jdbc.update(statementAfterRelease, graduationRequestId);
            }
        }));
    }

    @Test
    void issuanceWaitsForTheDecisionOnTheSameGraduationAndThenSeesItDisabled() throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = holdRowLockUntil(locked, release,
                "update graduation_request set certificate_review_status = 'DISABLED', certificate_reviewed_by_id = "
                        + staffId + ", certificate_reviewed_at = now() where id = ?");
        locked.await();

        Future<?> issuing = executor.submit(() -> asStaff(() -> issueUseCase.issue(
                new IssueGraduationCertificateRequest(GraduationCertificateKind.PROGRAM, enrollmentId))));

        assertThatThrownBy(() -> issuing.get(BLOCKED_WINDOW_MILLIS, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();
        holder.get(30, TimeUnit.SECONDS);
        assertThatThrownBy(() -> issuing.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(GraduationCertificateReviewRequiredException.class);
        assertThat(jdbc.queryForObject("select count(*) from graduation_certificate where program_enrollment_id = ?",
                Integer.class, enrollmentId)).isZero();
    }

    @Test
    void decisionWaitsForAnIssuanceOnTheSameGraduationAndThenRefusesBecauseTheCertificateIsValid() throws Exception {
        CountDownLatch issued = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> issuing = executor.submit(() -> asStaff(() -> {
            transactionTemplate.executeWithoutResult(status -> {
                issueUseCase.issue(new IssueGraduationCertificateRequest(GraduationCertificateKind.PROGRAM, enrollmentId));
                issued.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            return null;
        }));
        issued.await();

        Future<Long> deciding = executor.submit(() -> asStaff(() ->
                decider.decide(graduationRequestId, CertificateReviewDecision.DISABLE, "Documentación incompleta del alumno")));

        assertThatThrownBy(() -> deciding.get(BLOCKED_WINDOW_MILLIS, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
        release.countDown();
        issuing.get(30, TimeUnit.SECONDS);
        assertThatThrownBy(() -> deciding.get(30, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(InvalidGraduationCertificateReviewDecisionException.class);
        assertThat(jdbc.queryForObject("select certificate_review_status from graduation_request where id = ?", String.class,
                graduationRequestId)).isEqualTo("ENABLED");
    }
}
