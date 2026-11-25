package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.evaluation.api.OwnExamRegistrationFinder;
import com.red_escuela.backend.evaluation.api.OwnExamRegistrationView;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.RecipientType;
import com.red_escuela.backend.graduation.certificate.exam.entity.ExamCertificate;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.examregistration.exception.InvalidExamRegistrationRecipientException;
import com.red_escuela.backend.graduation.certificate.specialist.CertificateDocumentAuditRecorder;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureDocumentNotFoundException;
import com.red_escuela.backend.identity.api.CurrentActorFinder;
import com.red_escuela.backend.identity.api.StudentFinder;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnExamCertificateIssuer {

    private static final int RECIPIENT_MAX_LENGTH = 80;
    private static final String DOCUMENT_TYPE = CertificateDocumentType.EXAM_CERTIFICATE.name();

    private final CurrentActorFinder currentActorFinder;
    private final StudentFinder studentFinder;
    private final OwnExamRegistrationFinder registrationFinder;
    private final ExamCertificateIssuer issuer;
    private final DailyIssuanceLimit dailyIssuanceLimit;
    private final CertificateDocumentAuditRecorder auditRecorder;

    public OwnExamCertificateIssuer(CurrentActorFinder currentActorFinder, StudentFinder studentFinder,
                                    OwnExamRegistrationFinder registrationFinder, ExamCertificateIssuer issuer,
                                    DailyIssuanceLimit dailyIssuanceLimit, CertificateDocumentAuditRecorder auditRecorder) {
        this.currentActorFinder = currentActorFinder;
        this.studentFinder = studentFinder;
        this.registrationFinder = registrationFinder;
        this.issuer = issuer;
        this.dailyIssuanceLimit = dailyIssuanceLimit;
        this.auditRecorder = auditRecorder;
    }

    @Transactional(noRollbackFor = IssuanceLimitReachedException.class)
    public OwnDocumentResponse issueOrReuse(Long registrationId, RecipientType recipientType, String rawRecipientText) {
        String recipientText = rawRecipientText == null ? null : rawRecipientText.strip();
        validateRecipient(recipientType, recipientText);
        Long studentId = currentActorFinder.currentStudentId();
        studentFinder.requireByIdForUpdate(studentId);
        OwnExamRegistrationView registration = registrationId == null ? null
                : registrationFinder.findAttendedOwned(studentId, registrationId).orElse(null);
        if (registration == null) {
            throw new ProcedureDocumentNotFoundException();
        }
        Optional<ExamCertificate> current = issuer.currentValidOf(registration.registrationId());
        if (current.isPresent() && sameRecipient(current.get(), recipientType, recipientText)) {
            return responseOf(current.get());
        }
        if (dailyIssuanceLimit.isReachedForExamCertificate(studentId)) {
            throw new IssuanceLimitReachedException();
        }
        ExamCertificate issued = issuer.issueToSelf(registration, recipientType, recipientText);
        auditRecorder.generatedByStudent(DOCUMENT_TYPE, issued.getId(), issued.getPdfSha256(), issued.getRenderEngineVersion());
        return responseOf(issued);
    }

    private boolean sameRecipient(ExamCertificate current, RecipientType recipientType, String recipientText) {
        return current.getRecipientType() == recipientType && Objects.equals(current.getRecipientText(), recipientText);
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

    private OwnDocumentResponse responseOf(ExamCertificate certificate) {
        return new OwnDocumentResponse(CertificateDocumentType.EXAM_CERTIFICATE, certificate.getId(), certificate.getCode(),
                certificate.getStatus(), certificate.getIssuedAt(), certificate.getExpiresAt(), issuer.verificationUrlOf(certificate));
    }
}
