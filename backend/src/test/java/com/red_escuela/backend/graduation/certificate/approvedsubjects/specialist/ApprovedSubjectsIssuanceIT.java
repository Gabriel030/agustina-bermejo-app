package com.red_escuela.backend.graduation.certificate.approvedsubjects.specialist;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.academicstructure.entity.ProgramSubject;
import com.red_escuela.backend.academicstructure.entity.Subject;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.enrollment.entity.SubjectEquivalence;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificatePdf;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificate;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.entity.ApprovedSubjectsCertificateStatus;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.render.ApprovedSubjectsPdfRenderer;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.repository.ApprovedSubjectsCertificateRepository;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshot;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.snapshot.ApprovedSubjectsSnapshotFactory;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.template.PublishedTemplateFixtures;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Role;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import com.red_escuela.backend.identity.repository.RoleRepository;
import com.red_escuela.backend.shared.domain.Address;
import com.red_escuela.backend.shared.domain.Grade;
import com.red_escuela.backend.shared.domain.StatusType;
import com.red_escuela.backend.shared.time.MutableClock;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ApprovedSubjectsIssuanceIT extends AbstractIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 10, 0);

    @Autowired
    private TransactionTemplate transactions;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private ApprovedSubjectsCertificateIssuer issuer;
    @Autowired
    private ApprovedSubjectsCertificateRepository repository;
    @Autowired
    private ApprovedSubjectsPdfRenderer renderer;
    @Autowired
    private CertificateDigest digest;
    @Autowired
    private CertificateTemplateRenderSource templateSource;
    @Autowired
    private ApprovedSubjectsCertificatePdfProvider pdfProvider;

    private Long studentId;
    private Long personId;
    private Long enrollmentId;

    @BeforeEach
    void seed() {
        MutableClock.INSTANCE.setDateTime(NOW);
        deleteCertificates();
        transactions.executeWithoutResult(status -> {
            CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
            Institution institution = new Institution();
            institution.setName("Instituto de prueba");
            entityManager.persist(institution);
            Person person = persistPerson();
            Student student = new Student();
            student.setPerson(person);
            student.setInstitution(institution);
            student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
            entityManager.persist(student);
            Program program = fixtures.program();
            ProgramEnrollment enrollment = new ProgramEnrollment();
            enrollment.setStudent(student);
            enrollment.setProgram(program);
            enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
            enrollment.setEnrollmentDate(LocalDateTime.of(2026, 3, 1, 10, 0));
            entityManager.persist(enrollment);
            Subject subject = fixtures.subject();
            ProgramSubject entry = new ProgramSubject();
            entry.setProgram(program);
            entry.setSubject(subject);
            entry.setCredits(4);
            entry.setStatus(StatusType.ACTIVE);
            entityManager.persist(entry);
            SubjectEquivalence equivalence = new SubjectEquivalence();
            equivalence.setProgramEnrollment(enrollment);
            equivalence.setSubjectId(subject.getId());
            equivalence.setOriginInstitution("Otra institución");
            equivalence.setOriginSubjectName("Materia de origen");
            equivalence.setOriginScore(Grade.of(new BigDecimal("8")));
            equivalence.setRecognizedOn(LocalDate.of(2026, 5, 20));
            equivalence.setResolutionNumber("R-1");
            equivalence.setReason("Equivalencia de prueba");
            equivalence.setRecordedById(person.getId());
            equivalence.setRecordedAt(LocalDateTime.of(2026, 5, 20, 10, 0));
            entityManager.persist(equivalence);
            entityManager.flush();
            studentId = student.getId();
            personId = person.getId();
            enrollmentId = enrollment.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        deleteCertificates();
        MutableClock.INSTANCE.reset();
    }

    private Person persistPerson() {
        Address address = new Address();
        entityManager.persist(address);
        Person person = new Person();
        String suffix = UUID.randomUUID().toString();
        person.setName("Persona");
        person.setLastname(suffix);
        person.setDni(suffix.substring(0, 12));
        person.setUsername("approved." + suffix);
        person.setEmail("approved." + suffix + "@example.com");
        person.setAddress(address);
        person.setStatus(StatusType.ACTIVE);
        Role role = roleRepository.findByName("ALUMNO").orElseThrow();
        person.getRoles().add(role);
        entityManager.persist(person);
        entityManager.flush();
        return person;
    }

    private void deleteCertificates() {
        jdbc.update("delete from verifiable_document where document_type = 'APPROVED_SUBJECTS_CERTIFICATE'");
        jdbc.update("delete from approved_subjects_certificate");
    }

    private long certificateCount() {
        return jdbc.queryForObject("select count(*) from approved_subjects_certificate", Long.class);
    }

    @Test
    void issueStoresTheCertificateRegistersItAsVerifiableAndAuditsIt() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);

        assertThat(response.status()).isEqualTo(DocumentValidityStatus.VALID);
        assertThat(response.approvedSubjectsCount()).isEqualTo(1);
        assertThat(response.enrollmentId()).isEqualTo(enrollmentId);
        assertThat(response.expiresAt()).isAfter(NOW);
        assertThat(response.verificationUrl()).contains("/verificar/");
        assertThat(jdbc.queryForObject(
                "select count(*) from verifiable_document where document_type = 'APPROVED_SUBJECTS_CERTIFICATE' and source_id = ?",
                Long.class, response.id())).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "select count(*) from audit_log where action = 'APPROVED_SUBJECTS_CERT_ISSUED' and entity_type = 'CERTIFICATE_DOCUMENT' and entity_id = ?",
                Long.class, response.id())).isEqualTo(1L);
    }

    @Test
    void storedHashMatchesARegenerationOfTheStoredSnapshot() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);

        ApprovedSubjectsCertificate stored = repository.findById(response.id()).orElseThrow();
        byte[] regenerated = renderer.render(stored.getSnapshot(), stored.certificateCode(),
                templateSource.assetsOf(templateSource.byId(stored.getTemplateVersionId()), stored.getRegistryNumber()));
        assertThat(digest.sha256(regenerated)).isEqualTo(stored.getPdfSha256());
    }

    @Test
    void issuanceWithPublishedVersionStoresItsIdAndTemplateReference() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);

        Map<String, Object> row = jdbc.queryForMap("select template_version_id, template_ref from approved_subjects_certificate where id = ?",
                response.id());
        assertThat(((Number) row.get("template_version_id")).longValue())
                .isEqualTo(PublishedTemplateFixtures.currentPublishedId(jdbc, "APPROVED_SUBJECTS_CERTIFICATE"));
        assertThat(row.get("template_ref")).isEqualTo(PublishedTemplateFixtures.currentPublishedRef(jdbc, "APPROVED_SUBJECTS_CERTIFICATE"));
    }

    @Test
    void republishingTheTemplateDoesNotChangeTheRegeneratedPdfNorItsHash() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);
        ApprovedSubjectsCertificate stored = repository.findById(response.id()).orElseThrow();
        PublishedTemplateFixtures.publishRebrandedVersion(jdbc, "APPROVED_SUBJECTS_CERTIFICATE");

        byte[] regenerated = renderer.render(stored.getSnapshot(), stored.certificateCode(),
                templateSource.assetsOf(templateSource.byId(stored.getTemplateVersionId()), stored.getRegistryNumber()));

        assertThat(digest.sha256(regenerated)).isEqualTo(stored.getPdfSha256());
    }

    @Test
    void ownDownloadOfACertificateIssuedWithAPublishedTemplateReturnsTheStoredHashEveryTime() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);
        ApprovedSubjectsCertificate stored = repository.findById(response.id()).orElseThrow();

        ApprovedSubjectsCertificatePdf first = pdfProvider.pdfOfOwn(response.id(), studentId);
        ApprovedSubjectsCertificatePdf second = pdfProvider.pdfOfOwn(response.id(), studentId);

        assertThat(stored.getTemplateVersionId()).isNotNull();
        assertThat(digest.sha256(first.content())).isEqualTo(stored.getPdfSha256());
        assertThat(digest.sha256(second.content())).isEqualTo(stored.getPdfSha256());
    }

    @Test
    void ownDownloadAfterRepublishingTheTemplateKeepsTheIssuedHashAndTheOriginalDesign() throws Exception {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);
        ApprovedSubjectsCertificate stored = repository.findById(response.id()).orElseThrow();
        PublishedTemplateFixtures.publishRebrandedVersion(jdbc, "APPROVED_SUBJECTS_CERTIFICATE");

        ApprovedSubjectsCertificatePdf downloaded = pdfProvider.pdfOfOwn(response.id(), studentId);

        assertThat(digest.sha256(downloaded.content())).isEqualTo(stored.getPdfSha256());
        assertThat(repository.findById(response.id()).orElseThrow().getTemplateVersionId()).isEqualTo(stored.getTemplateVersionId());
        try (var document = Loader.loadPDF(downloaded.content())) {
            assertThat(new PDFTextStripper().getText(document)).doesNotContain("INSTITUTO REPUBLICADO");
        }
    }

    @Test
    void certificateIssuedAfterRepublishingUsesTheNewVersionAndKeepsTheOldOneUntouched() {
        ApprovedSubjectsCertificateResponse before = issuer.issue(studentId, enrollmentId, personId, NOW);
        Long republishedId = PublishedTemplateFixtures.publishRebrandedVersion(jdbc, "APPROVED_SUBJECTS_CERTIFICATE");

        ApprovedSubjectsCertificateResponse after = issuer.issue(studentId, enrollmentId, personId, NOW.plusDays(1));

        assertThat(repository.findById(after.id()).orElseThrow().getTemplateVersionId()).isEqualTo(republishedId);
        assertThat(repository.findById(before.id()).orElseThrow().getTemplateVersionId()).isNotEqualTo(republishedId);
    }

    @Test
    void certificateWithoutTemplateVersionRegeneratesTheBuiltInDesignWithTheSameHash() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);
        ApprovedSubjectsCertificate stored = repository.findById(response.id()).orElseThrow();
        ApprovedSubjectsSnapshot builtIn = stored.getSnapshot().withPublishedTemplate(stored.getSnapshot().institutionName(),
                stored.getSnapshot().signatories(), ApprovedSubjectsSnapshotFactory.TEMPLATE_REF);
        byte[] legacy = renderer.render(builtIn, stored.certificateCode());
        jdbc.update("update approved_subjects_certificate set template_version_id = null, template_ref = ?, pdf_sha256 = ?, "
                + "snapshot = jsonb_set(snapshot, '{templateRef}', to_jsonb(?::text)) where id = ?",
                ApprovedSubjectsSnapshotFactory.TEMPLATE_REF, digest.sha256(legacy), ApprovedSubjectsSnapshotFactory.TEMPLATE_REF,
                response.id());

        ApprovedSubjectsCertificate reloaded = repository.findById(response.id()).orElseThrow();

        assertThat(reloaded.getTemplateVersionId()).isNull();
        assertThat(digest.sha256(renderer.render(reloaded.getSnapshot(), reloaded.certificateCode()))).isEqualTo(reloaded.getPdfSha256());
    }

    @Test
    void secondIssueOnTheSameDayHitsTheDailyLimitAndStoresNothingMore() {
        issuer.issue(studentId, enrollmentId, personId, NOW);

        assertThatThrownBy(() -> issuer.issue(studentId, enrollmentId, personId, NOW.plusHours(1)))
                .isInstanceOf(IssuanceLimitReachedException.class);
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void issueTheNextDayKeepsThePreviousCertificateValid() {
        ApprovedSubjectsCertificateResponse first = issuer.issue(studentId, enrollmentId, personId, NOW);

        ApprovedSubjectsCertificateResponse second = issuer.issue(studentId, enrollmentId, personId, NOW.plusDays(1));

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(repository.findById(first.id()).orElseThrow().getStatus()).isEqualTo(ApprovedSubjectsCertificateStatus.VALID);
        assertThat(repository.findFirstByProgramEnrollmentIdAndStatusOrderByIssuedAtDesc(enrollmentId,
                ApprovedSubjectsCertificateStatus.VALID).orElseThrow().getId()).isEqualTo(second.id());
    }

    @Test
    void concurrentIssuesForTheSameStudentEmitExactlyOne() throws Exception {
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int index = 0; index < attempts; index++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    issuer.issue(studentId, enrollmentId, personId, NOW);
                    return true;
                } catch (IssuanceLimitReachedException limitReached) {
                    return false;
                }
            }));
        }
        start.countDown();
        long succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(1L);
        assertThat(certificateCount()).isEqualTo(1L);
    }

    @Test
    void repositoryReportsExistingCodesAndDailyCount() {
        ApprovedSubjectsCertificateResponse response = issuer.issue(studentId, enrollmentId, personId, NOW);

        LocalDateTime dayStart = NOW.toLocalDate().atStartOfDay();
        assertThat(repository.existsByCode(response.code())).isTrue();
        assertThat(repository.existsByCode("NO-SUCH-CODE")).isFalse();
        assertThat(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(studentId, dayStart, dayStart.plusDays(1)))
                .isEqualTo(1L);
        assertThat(repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(studentId, dayStart.plusDays(1),
                dayStart.plusDays(2))).isZero();
    }
}
