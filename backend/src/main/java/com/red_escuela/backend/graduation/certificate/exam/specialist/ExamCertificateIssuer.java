package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.repository.ExamCertificateRepository;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificateIssuer {

    private static final int CODE_ATTEMPTS = 3;

    private final ExamCertificateRepository repository;
    private final ExamCertificateTemplate template;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final StudentFinder studentFinder;
    private final CurrentActorFinder currentActorFinder;
    private final DocumentIssuanceSettings settings;
    private final AuditTrail auditTrail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ExamCertificateIssuer(ExamCertificateRepository repository, ExamCertificateTemplate template,
                                 CertificatePdfRenderer renderer, CertificateFileStore fileStore,
                                 CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                 StudentFinder studentFinder, CurrentActorFinder currentActorFinder,
                                 DocumentIssuanceSettings settings, AuditTrail auditTrail, Clock clock) {
        this.repository = repository;
        this.template = template;
        this.renderer = renderer;
        this.fileStore = fileStore;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.studentFinder = studentFinder;
        this.currentActorFinder = currentActorFinder;
        this.settings = settings;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ExamCertificate issue(ExamCertificateIssueCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        int validityDays = settings.current().examAttendanceValidityDays();
        LocalDateTime expiresAt = now.plusDays(validityDays);
        CertificateSnapshot snapshot = template.snapshotOf(studentFinder.requireById(command.studentId()), command.subjectName(),
                command.examDate(), now.toLocalDate());
        CertificateCode code = newUniqueCode();
        byte[] pdf = renderer.render(template.definitionFor(snapshot, expiresAt.toLocalDate(), command.recipientType(),
                command.recipientText()), snapshot, code, verificationUrl.of(code));
        String path = fileStore.save(code.value(), pdf);
        try {
            ExamCertificate saved = repository.saveAndFlush(ExamCertificate.builder()
                    .code(code.value())
                    .registrationId(command.registrationId())
                    .examBoardId(command.examBoardId())
                    .studentId(command.studentId())
                    .status(DocumentValidityStatus.VALID)
                    .origin(command.origin())
                    .issuedAt(now)
                    .expiresAt(expiresAt)
                    .validityDays(validityDays)
                    .issuedById(currentActorFinder.currentPersonId())
                    .recipientType(command.recipientType())
                    .recipientText(command.recipientText())
                    .snapshot(snapshot)
                    .templateRef(snapshot.templateRef())
                    .pdfSha256(digest.sha256(pdf))
                    .renderEngineVersion(CertificatePdfRenderer.ENGINE_VERSION)
                    .pdfPath(path)
                    .build());
            auditTrail.record(new AuditRecord(AuditAction.EXAM_CERTIFICATE_ISSUED, AuditEntityType.EXAM_CERTIFICATE, saved.getId(),
                    null, saved.getPdfSha256(), "examBoardId=" + saved.getExamBoardId() + ";registrationId=" + saved.getRegistrationId()));
            return saved;
        } catch (RuntimeException failure) {
            fileStore.deleteIfRolledBack(path);
            throw failure;
        }
    }

    public String verificationUrlOf(ExamCertificate certificate) {
        return verificationUrl.of(certificate.certificateCode());
    }

    private CertificateCode newUniqueCode() {
        for (int attempt = 0; attempt < CODE_ATTEMPTS; attempt++) {
            CertificateCode candidate = CertificateCode.generate(random);
            if (!repository.existsByCode(candidate.value())) {
                return candidate;
            }
        }
        throw new IllegalStateException("No se pudo generar un código de certificado único");
    }

    public record ExamCertificateIssueCommand(Long registrationId, Long examBoardId, Long studentId, String subjectName,
                                              LocalDate examDate, RecipientType recipientType, String recipientText,
                                              DocumentOrigin origin) {
    }
}
