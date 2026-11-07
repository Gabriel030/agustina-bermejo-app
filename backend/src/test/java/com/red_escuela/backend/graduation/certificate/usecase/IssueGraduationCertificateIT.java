package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.academicstructure.entity.Program;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateStatus;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateNotEligibleException;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class IssueGraduationCertificateIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private IssueGraduationCertificateUseCase issueUseCase;
    @Autowired
    private GetGraduationCertificatePdfUseCase pdfUseCase;
    @Autowired
    private GraduationCertificateRepository repository;
    @Autowired
    private CertificateFileStore fileStore;
    @Autowired
    private CertificateDigest digest;

    private Program program;
    private ProgramEnrollment enrollment;

    @BeforeEach
    void seedCompletedProgramEnrollment() {
        CycleTestFixtures fixtures = new CycleTestFixtures(entityManager);
        Institution institution = new Institution();
        institution.setName("Instituto de prueba");
        entityManager.persist(institution);
        Student student = new Student();
        student.setPerson(fixtures.person());
        student.setInstitution(institution);
        student.setOnboardingStatus(StudentOnboardingStatus.CONFIRMADO);
        entityManager.persist(student);
        program = fixtures.program();
        enrollment = new ProgramEnrollment();
        enrollment.setStudent(student);
        enrollment.setProgram(program);
        enrollment.setStatus(ProgramEnrollmentStatus.COMPLETED);
        enrollment.setEnrollmentDate(LocalDateTime.now().minusYears(3));
        enrollment.setCompletionDate(LocalDateTime.of(2026, 7, 15, 10, 0));
        entityManager.persist(enrollment);
        Person staff = fixtures.person();
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(staff.getId().toString(), null));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private GraduationCertificateResponse issue() {
        return issueUseCase.issue(new IssueGraduationCertificateRequest(GraduationCertificateKind.PROGRAM, enrollment.getId()));
    }

    private String textOf(byte[] pdf) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void issuesProgramCertificateAndStoresPdfWithMatchingHash() {
        GraduationCertificateResponse response = issue();

        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        byte[] file = fileStore.load(stored.getPdfPath()).orElseThrow();
        assertThat(response.status()).isEqualTo(GraduationCertificateStatus.VALID);
        assertThat(stored.getPdfSha256()).isEqualTo(digest.sha256(file));
        assertThat(response.verificationUrl()).endsWith("/verificar/" + response.code());
        assertThat(response.titleName()).isEqualTo(program.getName());
    }

    @Test
    void secondIssueForSameEnrollmentFails() {
        issue();

        assertThatThrownBy(this::issue).isInstanceOf(GraduationCertificateAlreadyIssuedException.class);
    }

    @Test
    void notCompletedEnrollmentCannotBeIssued() {
        enrollment.setStatus(ProgramEnrollmentStatus.ENROLLED);
        entityManager.flush();

        assertThatThrownBy(this::issue).isInstanceOf(GraduationCertificateNotEligibleException.class);
        assertThat(repository.count()).isZero();
    }

    @Test
    void downloadRegeneratesMissingFile() {
        GraduationCertificateResponse response = issue();
        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        String originalHash = stored.getPdfSha256();
        fileStore.deleteIfRolledBack(stored.getPdfPath());

        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(response.id());

        assertThat(fileStore.load(stored.getPdfPath())).isPresent();
        assertThat(stored.getPdfSha256()).isEqualTo(digest.sha256(pdf.content()));
        assertThat(pdf.code()).isEqualTo(response.code());
        assertThat(originalHash).hasSize(64);
    }

    @Test
    void downloadRegeneratesCorruptedFile() {
        GraduationCertificateResponse response = issue();
        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        fileStore.save(response.code(), new byte[] {1, 2, 3});

        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(response.id());

        assertThat(pdf.content()).hasSizeGreaterThan(100);
        assertThat(stored.getPdfSha256()).isEqualTo(digest.sha256(pdf.content()));
    }

    @Test
    void editingProgramNameDoesNotChangeIssuedCertificate() throws IOException {
        GraduationCertificateResponse response = issue();
        String originalName = program.getName();
        program.setName("Nombre nuevo de la carrera");
        entityManager.flush();
        entityManager.clear();

        GraduationCertificatePdf pdf = pdfUseCase.pdfOf(response.id());

        assertThat(textOf(pdf.content())).contains(originalName).doesNotContain("Nombre nuevo");
        assertThat(repository.findById(response.id()).orElseThrow().getSnapshot().titleName()).isEqualTo(originalName);
    }

    @Test
    void partialUniqueIndexRejectsSecondValidCertificateForTheSameEnrollment() {
        GraduationCertificateResponse first = issue();
        GraduationCertificate duplicate = GraduationCertificate.issue(GraduationCertificateKind.PROGRAM, enrollment.getId(),
                enrollment.getStudent().getId(), new CertificateCode("00000000000000000000000001"),
                repository.findById(first.id()).orElseThrow().getSnapshot(), "b".repeat(64), "00000000000000000000000001.pdf",
                LocalDateTime.now(), enrollment.getStudent().getPerson().getId());

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
