package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.api.DocumentIssuanceSettings;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.engine.CertificateCode;
import com.red_escuela.backend.graduation.certificate.engine.CertificateFileStore;
import com.red_escuela.backend.graduation.certificate.engine.CertificatePdfRenderer;
import com.red_escuela.backend.graduation.certificate.engine.CertificateSnapshot;
import com.red_escuela.backend.graduation.certificate.examregistration.entity.ExamRegistrationCertificate;
import com.red_escuela.backend.graduation.certificate.examregistration.exception.InvalidExamRegistrationRecipientException;
import com.red_escuela.backend.graduation.certificate.examregistration.repository.ExamRegistrationCertificateRepository;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationEligibilityEvaluator.ExamRegistrationSelection;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDigest;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateVerificationUrl;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureIneligibilityCode;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import com.red_escuela.backend.identity.entity.Student;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

import static com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType.EXAM_REGISTRATION_CERTIFICATE;

@Slf4j
@Service
public class ExamRegistrationCertificateIssuer {

    private static final int CODE_ATTEMPTS = 3;
    private static final int RECIPIENT_MAX_LENGTH = 80;

    private final ExamRegistrationEligibilityEvaluator evaluator;
    private final ExamRegistrationCertificateRepository repository;
    private final ExamRegistrationCertificateTemplate template;
    private final CertificatePdfRenderer renderer;
    private final CertificateFileStore fileStore;
    private final CertificateVerificationUrl verificationUrl;
    private final CertificateDigest digest;
    private final CertificateDocumentAuditRecorder auditRecorder;
    private final DocumentIssuanceSettings issuanceSettings;
    private final CurrentActorFinder currentActorFinder;
    private final StudentFinder studentFinder;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ExamRegistrationCertificateIssuer(ExamRegistrationEligibilityEvaluator evaluator,
                                             ExamRegistrationCertificateRepository repository,
                                             ExamRegistrationCertificateTemplate template, CertificatePdfRenderer renderer,
                                             CertificateFileStore fileStore, CertificateVerificationUrl verificationUrl,
                                             CertificateDigest digest, CertificateDocumentAuditRecorder auditRecorder,
                                             DocumentIssuanceSettings issuanceSettings, CurrentActorFinder currentActorFinder, StudentFinder studentFinder,
                                             MeterRegistry meterRegistry, Clock clock) {
        this.evaluator = evaluator;
        this.repository = repository;
        this.template = template;
        this.renderer = renderer;
        this.fileStore = fileStore;
        this.verificationUrl = verificationUrl;
        this.digest = digest;
        this.auditRecorder = auditRecorder;
        this.issuanceSettings = issuanceSettings;
        this.currentActorFinder = currentActorFinder;
        this.studentFinder = studentFinder;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Transactional
    public OwnDocumentResponse issueOrReuse(Long registrationId, RecipientType recipientType, String recipientText) {
        validateRecipient(recipientType, recipientText);
        Long studentId = currentActorFinder.currentStudentId();
        Student student = studentFinder.requireByIdForUpdate(studentId);
        LocalDateTime now = LocalDateTime.now(clock);
        ExamRegistrationSelection selection = evaluator.select(registrationId);
        requireEligible(selection.eligibility());
        OwnExamRegistrationView registration = selection.registration();
        Optional<ExamRegistrationCertificate> current = repository.findFirstByStudentIdAndRegistrationIdAndStatusOrderByIssuedAtDesc(
                studentId, registration.registrationId(), DocumentValidityStatus.VALID).filter(valid -> valid.isValidAt(now));
        if (current.isPresent() && sameRecipient(current.get(), recipientType, recipientText)) {
            return responseOf(current.get());
        }
        if (current.isPresent()) {
            requireDailyAllowance(studentId, now);
            current.get().markReplaced();
            repository.saveAndFlush(current.get());
        }
        return responseOf(issue(student, registration, recipientType, recipientText, now));
    }

    private ExamRegistrationCertificate issue(Student student, OwnExamRegistrationView registration, RecipientType recipientType,
                                              String recipientText, LocalDateTime now) {
        CertificateCode code = newUniqueCode();
        CertificateSnapshot snapshot = template.snapshotOf(student, registration);
        String url = verificationUrl.of(code);
        byte[] pdf = renderer.render(template.definitionFor(snapshot, recipientType, recipientText), snapshot, code, url);
        String path = fileStore.save(code.value(), pdf);
        try {
            ExamRegistrationCertificate saved = repository.saveAndFlush(ExamRegistrationCertificate.builder()
                    .code(code.value())
                    .studentId(student.getId())
                    .registrationId(registration.registrationId())
                    .examBoardId(registration.examBoardId())
                    .status(DocumentValidityStatus.VALID)
                    .issuedAt(now)
                    .expiresAt(registration.examDateTime().toLocalDate().plusDays(1).atStartOfDay())
                    .issuedById(currentActorFinder.currentPersonId())
                    .recipientType(recipientType)
                    .recipientText(recipientText)
                    .snapshot(snapshot)
                    .templateRef(snapshot.templateRef())
                    .pdfSha256(digest.sha256(pdf))
                    .pdfPath(path)
                    .renderEngineVersion(CertificatePdfRenderer.ENGINE_VERSION)
                    .build());
            auditRecorder.generatedByStudent(EXAM_REGISTRATION_CERTIFICATE.name(), saved.getId(), saved.getPdfSha256(),
                    saved.getRenderEngineVersion());
            meterRegistry.counter("exam_registration_certificate_issued_total").increment();
            log.info("Constancia de inscripción a examen emitida: certificateId={}, registrationId={}", saved.getId(),
                    registration.registrationId());
            return saved;
        } catch (RuntimeException failure) {
            fileStore.deleteIfRolledBack(path);
            throw failure;
        }
    }

    private boolean sameRecipient(ExamRegistrationCertificate current, RecipientType recipientType, String recipientText) {
        return current.getRecipientType() == recipientType && Objects.equals(current.getRecipientText(), recipientText);
    }

    private void requireDailyAllowance(Long studentId, LocalDateTime now) {
        LocalDateTime dayStart = now.toLocalDate().atStartOfDay();
        long issuedToday = repository.countByStudentIdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(studentId, dayStart,
                dayStart.plusDays(1));
        if (issuedToday >= issuanceSettings.current().dailyIssuanceLimitPerType()) {
            throw new IssuanceLimitReachedException();
        }
    }

    private void validateRecipient(RecipientType recipientType, String recipientText) {
        if ((recipientType == null) != (recipientText == null)) {
            throw new InvalidExamRegistrationRecipientException("El destinatario requiere tipo y texto juntos");
        }
        if (recipientText == null) {
            return;
        }
        if (recipientText.isBlank() || recipientText.length() > RECIPIENT_MAX_LENGTH) {
            throw new InvalidExamRegistrationRecipientException("El destinatario debe tener entre 1 y 80 caracteres y no puede ser solo espacios");
        }
        if (recipientText.indexOf('{') >= 0 || recipientText.indexOf('}') >= 0) {
            throw new InvalidExamRegistrationRecipientException("El destinatario no puede contener llaves");
        }
    }

    private void requireEligible(ProcedureEligibilityResponse eligibility) {
        if (eligibility.eligible()) {
            return;
        }
        boolean limitReached = eligibility.reasons().stream()
                .anyMatch(reason -> reason.code() == ProcedureIneligibilityCode.ISSUANCE_LIMIT_REACHED);
        if (limitReached) {
            throw new IssuanceLimitReachedException();
        }
        throw new ProcedureNotEligibleException();
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

    private OwnDocumentResponse responseOf(ExamRegistrationCertificate certificate) {
        return new OwnDocumentResponse(EXAM_REGISTRATION_CERTIFICATE, certificate.getId(), certificate.getCode(), certificate.getStatus(),
                certificate.getIssuedAt(), certificate.getExpiresAt(), verificationUrl.of(certificate.certificateCode()));
    }
}
