package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class GraduationCertificateRegistryConcurrencyIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private TransactionTemplate transactionTemplate;
    @Autowired
    private IssueGraduationCertificateUseCase issueUseCase;
    @Autowired
    private GraduationCertificateRepository repository;

    private ProgramEnrollment seedEnrollment(CycleTestFixtures fixtures, Institution institution) {
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
        return enrollment;
    }

    @Test
    void twoConcurrentIssuancesGetDifferentConsecutiveRegistryNumbers() throws Exception {
        List<Long> enrollmentIds = new ArrayList<>();
        Long[] staffId = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            enrollmentIds.add(seedEnrollment(fixtures, institution).getId());
            enrollmentIds.add(seedEnrollment(fixtures, institution).getId());
            Person staff = fixtures.person();
            entityManager.flush();
            staffId[0] = staff.getId();
        });
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<GraduationCertificateResponse>> futures = new ArrayList<>();
            for (Long enrollmentId : enrollmentIds) {
                Callable<GraduationCertificateResponse> task = () -> {
                    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staffId[0].toString(), null));
                    try {
                        barrier.await();
                        return issueUseCase.issue(new IssueGraduationCertificateRequest(GraduationCertificateKind.PROGRAM, enrollmentId));
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                };
                futures.add(executor.submit(task));
            }
            List<String> numbers = new ArrayList<>();
            for (Future<GraduationCertificateResponse> future : futures) {
                GraduationCertificate stored = repository.findById(future.get().id()).orElseThrow();
                numbers.add(stored.getRegistryNumber());
            }
            int year = LocalDateTime.now().getYear();
            assertThat(numbers).containsExactlyInAnyOrder("0000001/" + year, "0000002/" + year);
        } finally {
            executor.shutdownNow();
        }
    }
}
