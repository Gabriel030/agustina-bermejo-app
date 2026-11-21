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
import com.red_escuela.backend.graduation.certificate.specialist.CertificateRegistryNumberAssigner;
import com.red_escuela.backend.graduation.certificate.template.dto.TemplatePreviewSource;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplatePreviewer;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
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
    @Autowired
    private CertificateRegistryNumberAssigner assigner;
    @Autowired
    private CertificateTemplatePreviewer templatePreviewer;
    @Autowired
    private JdbcTemplate jdbc;

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

    private int counterValue() {
        List<Integer> values = jdbc.queryForList("select last_number from graduation_certificate_registry_counter where kind = 'PROGRAM' and registry_year = ?",
                Integer.class, LocalDateTime.now().getYear());
        return values.isEmpty() ? 0 : values.get(0);
    }

    @Test
    void rolledBackAssignmentReleasesTheNumberWithoutLeavingAGap() {
        String committed = transactionTemplate.execute(status -> assigner.next(GraduationCertificateKind.PROGRAM));
        String rolledBack = transactionTemplate.execute(status -> {
            String number = assigner.next(GraduationCertificateKind.PROGRAM);
            status.setRollbackOnly();
            return number;
        });
        String reused = transactionTemplate.execute(status -> assigner.next(GraduationCertificateKind.PROGRAM));

        assertThat(rolledBack).isEqualTo(reused);
        assertThat(Integer.parseInt(reused.substring(0, 7))).isEqualTo(Integer.parseInt(committed.substring(0, 7)) + 1);
    }

    @Test
    void previewsDoNotConsumeTheRegistryCounter() {
        transactionTemplate.executeWithoutResult(status -> assigner.next(GraduationCertificateKind.PROGRAM));
        int before = counterValue();

        templatePreviewer.preview(GraduationCertificateKind.PROGRAM, TemplatePreviewSource.PUBLISHED);
        templatePreviewer.preview(GraduationCertificateKind.PROGRAM, TemplatePreviewSource.PUBLISHED);

        assertThat(counterValue()).isEqualTo(before);
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
            List<Integer> sequence = numbers.stream().map(number -> Integer.parseInt(number.substring(0, 7))).sorted().toList();
            assertThat(numbers).allMatch(number -> number.endsWith("/" + year)).doesNotHaveDuplicates();
            assertThat(sequence.get(1)).isEqualTo(sequence.get(0) + 1);
        } finally {
            executor.shutdownNow();
        }
    }
}
