package com.red_escuela.backend.graduation.certificate.usecase;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import com.red_escuela.backend.academicstructure.CycleTestFixtures;
import com.red_escuela.backend.config.AbstractIntegrationTest;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollment;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentStatus;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateResponse;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificate;
import com.red_escuela.backend.graduation.certificate.entity.GraduationCertificateKind;
import com.red_escuela.backend.graduation.certificate.repository.GraduationCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateRegistryNumberAssigner;
import com.red_escuela.backend.graduation.certificate.template.dto.CertificateTemplateContent;
import com.red_escuela.backend.graduation.certificate.template.usecase.PublishCertificateTemplateUseCase;
import com.red_escuela.backend.graduation.certificate.template.usecase.SaveCertificateTemplateDraftUseCase;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateStatus;
import com.red_escuela.backend.graduation.certificate.verification.dto.PublicCertificateView;
import com.red_escuela.backend.graduation.certificate.verification.usecase.VerifyPublicCertificateUseCase;
import com.red_escuela.backend.identity.entity.Institution;
import com.red_escuela.backend.identity.entity.Person;
import com.red_escuela.backend.identity.entity.Student;
import com.red_escuela.backend.identity.entity.StudentOnboardingStatus;
import jakarta.persistence.EntityManager;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GraduationCertificateTemplateRenderIT extends AbstractIntegrationTest {

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
    private SaveCertificateTemplateDraftUseCase saveDraft;
    @Autowired
    private PublishCertificateTemplateUseCase publish;
    @Autowired
    private VerifyPublicCertificateUseCase verifyUseCase;
    @Autowired
    private CertificateRegistryNumberAssigner registryNumberAssigner;
    @Autowired
    private JdbcTemplate jdbc;
    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    private ProgramEnrollment enrollment;
    private com.red_escuela.backend.academicstructure.entity.Program program;

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

    private Long signatory(String name, String role) {
        Long id = jdbc.queryForObject("insert into certificate_signatory (name, role, active, created_at, updated_at) values (?, ?, true, now(), now()) returning id",
                Long.class, name, role);
        jdbc.update("insert into certificate_signatory_permission (signatory_id, document_type) values (?, 'GRADUATION_APPROVAL')", id);
        return id;
    }

    private void publishNewVersion(String title, String signerName) {
        Long first = signatory(signerName, "Rectora");
        Long second = signatory("Luis Firmante", "Secretario");
        saveDraft.save(GraduationCertificateKind.PROGRAM, new CertificateTemplateContent("INSTITUTO NUEVO", null, title, "Por la presente",
                "ha aprobado {titleKind}", "Dictada por la institución", "VALIDÁ ESTE DOCUMENTO", "Leyenda nueva {registryNumber}",
                null, null, first, second));
        publish.publish(GraduationCertificateKind.PROGRAM);
        entityManager.flush();
    }

    private String decodeQr(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            BufferedImage image = new PDFRenderer(document).renderImageWithDPI(0, 300);
            int[] pixels = image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
            return new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(
                    new RGBLuminanceSource(image.getWidth(), image.getHeight(), pixels)))).getText();
        }
    }

    @Test
    void issuanceUsesPublishedVersionAndStoresItWithTheRegistryNumber() throws Exception {
        GraduationCertificateResponse response = issue();

        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        Long versionOne = jdbc.queryForObject("select v.id from certificate_template_version v join certificate_template t on t.id = v.template_id "
                + "where t.kind = 'PROGRAM' and v.version_number = 1", Long.class);
        assertThat(stored.getTemplateVersionId()).isEqualTo(versionOne);
        assertThat(stored.getRegistryNumber()).matches("0000001/" + LocalDateTime.now().getYear());
        assertThat(stored.getSnapshot().templateRef()).isEqualTo("TEMPLATE_V1");
        String text = textOf(fileStore.load(stored.getPdfPath()).orElseThrow());
        assertThat(text).contains("CERTIFICADO DE APROBACIÓN", "Dirección", "Secretaria Legal", "Director Académico",
                "REG. DE CERTIF. N°: " + stored.getRegistryNumber());
    }

    @Test
    void publishingANewVersionDoesNotChangeTheRegeneratedPdfOfAnIssuedCertificate() throws Exception {
        GraduationCertificateResponse first = issue();
        Long issuedWith = repository.findById(first.id()).orElseThrow().getTemplateVersionId();
        publishNewVersion("DIPLOMA NUEVO", "Ana Nueva");
        GraduationCertificate stored = repository.findById(first.id()).orElseThrow();
        fileStore.deleteIfRolledBack(stored.getPdfPath());
        entityManager.clear();

        GraduationCertificatePdf regenerated = pdfUseCase.pdfOf(first.id());

        String text = textOf(regenerated.content());
        assertThat(text).contains("CERTIFICADO DE APROBACIÓN").doesNotContain("DIPLOMA NUEVO", "Ana Nueva", "INSTITUTO NUEVO");
        assertThat(repository.findById(first.id()).orElseThrow().getTemplateVersionId()).isEqualTo(issuedWith);
    }

    @Test
    void newIssuanceAfterPublishingUsesTheNewVersionAndItsSigners() throws Exception {
        publishNewVersion("DIPLOMA NUEVO", "Ana Nueva");

        GraduationCertificateResponse response = issue();

        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        String text = textOf(fileStore.load(stored.getPdfPath()).orElseThrow());
        assertThat(text).contains("DIPLOMA NUEVO", "Ana Nueva", "Rectora", "Luis Firmante", "INSTITUTO NUEVO");
        assertThat(text).doesNotContain("Secretaria Legal", "Director Académico");
        assertThat(stored.getSnapshot().templateRef()).isEqualTo("TEMPLATE_V2");
        assertThat(stored.getSnapshot().signatories()).extracting("name").containsExactly("Ana Nueva", "Luis Firmante");
    }

    @Test
    void builtInCertificatesWithoutTemplateVersionRegenerateWithTheBuiltInWording() throws Exception {
        GraduationCertificateResponse response = issue();
        jdbc.update("update graduation_certificate set template_version_id = null, registry_number = null where id = ?", response.id());
        entityManager.clear();
        GraduationCertificate legacy = repository.findById(response.id()).orElseThrow();
        fileStore.deleteIfRolledBack(legacy.getPdfPath());
        publishNewVersion("DIPLOMA NUEVO", "Ana Nueva");
        entityManager.clear();

        String text = textOf(pdfUseCase.pdfOf(response.id()).content());

        assertThat(text).contains("CERTIFICADO DE APROBACIÓN", "Se certifica que").doesNotContain("DIPLOMA NUEVO", "REG. DE CERTIF.");
        assertThat(repository.findById(response.id()).orElseThrow().getRegistryNumber()).isNull();
    }

    @Test
    void registryNumbersAreConsecutiveAndSeparateByKindAndYear() {
        String first = registryNumberAssigner.next(GraduationCertificateKind.PROGRAM);
        String second = registryNumberAssigner.next(GraduationCertificateKind.PROGRAM);
        String course = registryNumberAssigner.next(GraduationCertificateKind.COURSE);
        int year = LocalDateTime.now().getYear();

        assertThat(first).isEqualTo("0000001/" + year);
        assertThat(second).isEqualTo("0000002/" + year);
        assertThat(course).isEqualTo("0000001/" + year);
    }

    @Test
    void generatedQrDecodesToTheVerificationUrlAndPublicVerificationReturnsTheDataAndRevokedState() throws Exception {
        GraduationCertificateResponse response = issue();
        GraduationCertificate stored = repository.findById(response.id()).orElseThrow();
        byte[] pdf = fileStore.load(stored.getPdfPath()).orElseThrow();

        assertThat(decodeQr(pdf)).isEqualTo(frontendBaseUrl.replaceAll("/$", "") + "/verificar/" + stored.getCode());

        PublicCertificateView valid = verifyUseCase.verify(stored.getCode(), "client-" + UUID.randomUUID());
        assertThat(valid.status()).isEqualTo(PublicCertificateStatus.VALID);
        assertThat(valid.holderFullName()).isEqualTo(stored.getSnapshot().holderFullName());
        assertThat(valid.titleName()).isEqualTo(stored.getSnapshot().titleName());
        assertThat(valid.registryNumber()).isEqualTo(stored.getRegistryNumber());

        jdbc.update("update graduation_certificate set status = 'REVOKED', revoked_at = now() where id = ?", stored.getId());
        entityManager.clear();

        assertThat(verifyUseCase.verify(stored.getCode(), "client-" + UUID.randomUUID()).status()).isEqualTo(PublicCertificateStatus.REVOKED);
    }
}
