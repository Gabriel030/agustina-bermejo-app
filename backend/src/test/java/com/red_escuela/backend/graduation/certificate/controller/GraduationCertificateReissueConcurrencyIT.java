package com.red_escuela.backend.graduation.certificate.controller;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotRevocableException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.usecase.IssueGraduationCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.usecase.ReissueGraduationCertificateUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
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
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class GraduationCertificateReissueConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IssueGraduationCertificateUseCase issueUseCase;
    @Autowired
    private ReissueGraduationCertificateUseCase reissueUseCase;
    @Autowired
    private GraduationCertificateRepository repository;
    @Autowired
    private JdbcTemplate jdbc;

    private Long enrollmentId;
    private Long staffId;

    @BeforeEach
    void seed() {
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
            Person staff = fixtures.person();
            entityManager.flush();
            enrollmentId = enrollment.getId();
            staffId = staff.getId();
        });
    }

    private <T> T asStaff(Callable<T> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staffId.toString(), null));
        try {
            return action.call();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Long issueOriginal() throws Exception {
        String code = asStaff(() -> issueUseCase.issue(new IssueGraduationCertificateRequest(GraduationCertificateKind.PROGRAM, enrollmentId))).code();
        return repository.findByCode(code).orElseThrow().getId();
    }

    @Test
    void failedIssuanceLeavesTheOriginalCertificateValid() throws Exception {
        Long originalId = issueOriginal();
        jdbc.update("update program_enrollment set status = 'ENROLLED' where id = ?", enrollmentId);

        assertThatThrownBy(() -> asStaff(() -> reissueUseCase.reissue(originalId, "Error en el apellido del titular")))
                .isInstanceOf(GraduationCertificateNotEligibleException.class);

        GraduationCertificate original = repository.findById(originalId).orElseThrow();
        assertThat(original.getStatus()).isEqualTo(GraduationCertificateStatus.VALID);
        assertThat(original.getRevokedAt()).isNull();
        assertThat(original.getRevocationReason()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from graduation_certificate where replaces_id = ?", Integer.class, originalId)).isZero();
    }

    @Test
    void twoConcurrentReissuesOfTheSameCertificateLetOnlyOneWin() throws Exception {
        Long originalId = issueOriginal();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Long>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> asStaff(() -> {
                    barrier.await();
                    return reissueUseCase.reissue(originalId, "Error en el apellido del titular").id();
                })));
            }
            int winners = 0;
            int conflicts = 0;
            for (Future<Long> future : futures) {
                try {
                    future.get();
                    winners++;
                } catch (ExecutionException e) {
                    assertThat(e.getCause()).isInstanceOf(GraduationCertificateNotRevocableException.class);
                    conflicts++;
                }
            }
            assertThat(winners).isEqualTo(1);
            assertThat(conflicts).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from graduation_certificate where replaces_id = ?", Integer.class, originalId)).isEqualTo(1);
            assertThat(repository.findById(originalId).orElseThrow().getStatus()).isEqualTo(GraduationCertificateStatus.REVOKED);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void aCertificateCanOnlyBeReplacedOnceAtDatabaseLevel() throws Exception {
        Long firstId = issueOriginal();
        Long secondId = asStaff(() -> reissueUseCase.reissue(firstId, "Error en el apellido del titular")).id();
        Long thirdId = asStaff(() -> reissueUseCase.reissue(secondId, "Error en la fecha de egreso")).id();

        assertThatThrownBy(() -> jdbc.update("update graduation_certificate set replaces_id = ? where id = ?", firstId, thirdId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
