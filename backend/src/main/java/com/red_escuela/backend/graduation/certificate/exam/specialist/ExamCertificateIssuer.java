package com.red_escuela.backend.graduation.certificate.exam.specialist;

import com.red_escuela.backend.evaluation.api.ExamBoardAttendance;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceReader;
import com.red_escuela.backend.evaluation.api.ExamBoardAttendanceView;
import com.red_escuela.backend.evaluation.api.ExamBoardRegistrationFinder;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateBoardNotHeldException;
import com.red_escuela.backend.graduation.certificate.exam.exception.ExamCertificateRegistrationNotEligibleException;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.graduation.certificate.dto.DocumentOrigin;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.engine.CertificateTemplateDefinition;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.graduation.certificate.template.entity.CertificateTemplateVersion;
import com.red_escuela.backend.graduation.certificate.template.specialist.CertificateTemplateRenderSource;
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
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExamCertificateIssuer {

    private static final int CODE_ATTEMPTS = 3;
    private static final DateTimeFormatter EXAM_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ExamCertificateRepository repository;
    private final ExamCertificateTemplate template;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final StudentFinder studentFinder;
    private final CurrentActorFinder currentActorFinder;
    private final DocumentIssuanceSettingsFinder settings;
    private final AuditTrail auditTrail;
    private final Clock clock;
    private final CertificateTemplateRenderSource templateSource;
    private final ExamBoardRegistrationFinder registrationFinder;
    private final ExamBoardAttendanceReader boardReader;
    private final SecureRandom random = new SecureRandom();

    public ExamCertificateIssuer(ExamCertificateRepository repository, ExamCertificateTemplate template,
                                 CertificatePdfRenderer renderer, CertificateFileStore fileStore,
                                 CertificateVerificationUrl verificationUrl, CertificateDigest digest,
                                 StudentFinder studentFinder, CurrentActorFinder currentActorFinder,
                                 DocumentIssuanceSettingsFinder settings, AuditTrail auditTrail, Clock clock,
                                 CertificateTemplateRenderSource templateSource, ExamBoardRegistrationFinder registrationFinder,
                                 ExamBoardAttendanceReader boardReader) {
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
        this.templateSource = templateSource;
        this.registrationFinder = registrationFinder;
        this.boardReader = boardReader;
    }

    @Transactional
    public ExamCertificate issueUnderDebtException(Long studentId, Long registrationId) {
        Long examBoardId = registrationFinder.requireById(registrationId).examBoardId();
        ExamBoardAttendanceView board = boardReader.attendanceForUpdateOf(examBoardId);
        if (!board.held()) {
            throw new ExamCertificateBoardNotHeldException();
        }
        boolean present = board.attendees().stream().anyMatch(attendee -> attendee.registrationId().equals(registrationId)
                && attendee.studentId().equals(studentId) && attendee.attendance() == ExamBoardAttendance.PRESENT);
        if (!present) {
            throw new ExamCertificateRegistrationNotEligibleException();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<ExamCertificate> current = repository.findByRegistrationIdAndStatus(registrationId, DocumentValidityStatus.VALID);
        if (current.isPresent() && !current.get().isExpiredAt(now)) {
            return current.get();
        }
        if (current.isPresent()) {
            current.get().expire();
            repository.saveAndFlush(current.get());
        }
        return issue(new ExamCertificateIssueCommand(registrationId, examBoardId, studentId, board.subjectName(), board.examDate(),
                null, null, DocumentOrigin.ISSUED_BY_STAFF));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ExamCertificate issue(ExamCertificateIssueCommand command) {
        LocalDateTime now = LocalDateTime.now(clock);
        int validityDays = settings.current().examAttendanceValidityDays();
        LocalDateTime expiresAt = now.plusDays(validityDays);
        CertificateSnapshot builtInSnapshot = template.snapshotOf(studentFinder.requireById(command.studentId()), command.subjectName(),
                command.examDate(), now.toLocalDate());
        Optional<CertificateTemplateVersion> publishedVersion = templateSource.findCurrentPublished(DocumentTemplateType.EXAM_CERTIFICATE);
        CertificateSnapshot snapshot = publishedVersion
                .map(version -> templateSource.snapshotWith(builtInSnapshot.withExtraMarkers(extraMarkersOf(command)), version))
                .orElse(builtInSnapshot);
        CertificateCode code = newUniqueCode();
        CertificateTemplateDefinition definition = template.definitionFor(snapshot, expiresAt.toLocalDate(), command.recipientType(),
                command.recipientText());
        String url = verificationUrl.of(code);
        byte[] pdf = publishedVersion
                .map(version -> renderer.render(definition, snapshot, code, url, templateSource.assetsOf(version, null)))
                .orElseGet(() -> renderer.render(definition, snapshot, code, url));
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
                    .templateVersionId(publishedVersion.map(CertificateTemplateVersion::getId).orElse(null))
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

    private Map<String, String> extraMarkersOf(ExamCertificateIssueCommand command) {
        Map<String, String> markers = new HashMap<>();
        markers.put("subjectName", command.subjectName());
        markers.put("examDate", command.examDate().format(EXAM_DATE_FORMAT));
        if (command.recipientText() != null) {
            markers.put("recipient", command.recipientText());
        }
        return markers;
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
