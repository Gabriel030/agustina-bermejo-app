package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.dto.DocumentValidityStatus;
import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateChannel;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularityKind;
import com.red_escuela.backend.graduation.certificate.regular.exception.NotRegularException;
import com.red_escuela.backend.graduation.certificate.regular.exception.RegularCertificateAlreadyIssuedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StandingNotConfirmedException;
import com.red_escuela.backend.graduation.certificate.regular.exception.StudentHasDebtException;
import com.red_escuela.backend.graduation.certificate.regular.specialist.IssueRegularCertificateCommand;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.exception.IssuanceLimitReachedException;
import com.red_escuela.backend.graduation.procedure.exception.ProcedureNotEligibleException;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.audit.AuditAction;
import com.red_escuela.backend.shared.audit.AuditEntityType;
import com.red_escuela.backend.shared.audit.AuditRecord;
import com.red_escuela.backend.shared.audit.service.AuditTrail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OwnRegularDocumentIssuer {

    private final CurrentStudentService currentStudentService;
    private final OwnProgramEnrollmentResolver enrollmentResolver;
    private final DailyIssuanceLimit dailyIssuanceLimit;
    private final RecipientLabelFormatter recipientLabelFormatter;
    private final RegularCertificateIssuer issuer;
    private final AuditTrail auditTrail;

    public OwnRegularDocumentIssuer(CurrentStudentService currentStudentService, OwnProgramEnrollmentResolver enrollmentResolver,
                                    DailyIssuanceLimit dailyIssuanceLimit, RecipientLabelFormatter recipientLabelFormatter,
                                    RegularCertificateIssuer issuer, AuditTrail auditTrail) {
        this.currentStudentService = currentStudentService;
        this.enrollmentResolver = enrollmentResolver;
        this.dailyIssuanceLimit = dailyIssuanceLimit;
        this.recipientLabelFormatter = recipientLabelFormatter;
        this.issuer = issuer;
        this.auditTrail = auditTrail;
    }

    @Transactional(noRollbackFor = {ProcedureNotEligibleException.class, IssuanceLimitReachedException.class,
            NotRegularException.class, StudentHasDebtException.class, StandingNotConfirmedException.class,
            RegularCertificateAlreadyIssuedException.class})
    public OwnDocumentResponse issue(GenerateOwnDocumentRequest request) {
        GenerateOwnDocumentRequest safe = request == null ? new GenerateOwnDocumentRequest(null, null, null, null) : request;
        Long studentId = currentStudentService.resolveCurrentStudent().getId();
        String recipientLabel = recipientLabelFormatter.labelOf(safe.recipientType(), safe.recipientText());
        Long enrollmentId = enrollmentResolver.resolve(studentId, safe.enrollmentId()).enrollmentId()
                .orElseThrow(ProcedureNotEligibleException::new);
        if (dailyIssuanceLimit.isReachedForRegular(studentId)) {
            throw new IssuanceLimitReachedException();
        }
        RegularCertificateResponse issued = issueOrReject(enrollmentId, recipientLabel);
        auditTrail.record(new AuditRecord(AuditAction.DOCUMENT_GENERATED_BY_STUDENT, AuditEntityType.CERTIFICATE_DOCUMENT,
                issued.id(), null, CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE.name(), null));
        return new OwnDocumentResponse(CertificateDocumentType.REGULAR_STUDENT_CERTIFICATE, issued.id(), issued.code(),
                DocumentValidityStatus.VALID, issued.issuedAt(), issued.expiresAt(), issued.verificationUrl());
    }

    private RegularCertificateResponse issueOrReject(Long enrollmentId, String recipientLabel) {
        try {
            return issuer.issueToSelf(new IssueRegularCertificateCommand(RegularityKind.PROGRAM, enrollmentId, false, null,
                    RegularCertificateChannel.STUDENT_PORTAL, null, recipientLabel));
        } catch (NotRegularException | StudentHasDebtException | StandingNotConfirmedException notEligible) {
            throw new ProcedureNotEligibleException();
        }
    }
}
