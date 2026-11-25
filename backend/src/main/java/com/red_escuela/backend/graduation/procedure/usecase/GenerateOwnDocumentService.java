package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.ApprovedSubjectsCertificateResponse;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.dto.RequestApprovedSubjectsCertificateRequest;
import com.red_escuela.backend.graduation.certificate.approvedsubjects.usecase.RequestApprovedSubjectsCertificateUseCase;
import com.red_escuela.backend.graduation.certificate.dto.CertificateDocumentType;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsOwnEnrollmentResolver;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.OwnRegularDocumentIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateOwnDocumentService implements GenerateOwnDocumentUseCase {

    private final OwnRegularDocumentIssuer regularIssuer;
    private final ExamRegistrationCertificateIssuer examRegistrationIssuer;

    private final ApprovedSubjectsOwnEnrollmentResolver approvedSubjectsEnrollmentResolver;
    private final RequestApprovedSubjectsCertificateUseCase requestApprovedSubjectsCertificate;

    public GenerateOwnDocumentService(OwnRegularDocumentIssuer regularIssuer, ExamRegistrationCertificateIssuer examRegistrationIssuer,
                                      ApprovedSubjectsOwnEnrollmentResolver approvedSubjectsEnrollmentResolver,
                                      RequestApprovedSubjectsCertificateUseCase requestApprovedSubjectsCertificate) {
        this.regularIssuer = regularIssuer;
        this.examRegistrationIssuer = examRegistrationIssuer;
        this.approvedSubjectsEnrollmentResolver = approvedSubjectsEnrollmentResolver;
        this.requestApprovedSubjectsCertificate = requestApprovedSubjectsCertificate;
    }

    @Override
    public OwnDocumentResponse generate(OwnDocumentType type, GenerateOwnDocumentRequest request) {
        if (type == OwnDocumentType.REGULAR_STUDENT_CERTIFICATE) {
            return regularIssuer.issue(request);
        }
        if (type == OwnDocumentType.EXAM_REGISTRATION_CERTIFICATE) {
            return examRegistrationIssuer.issueOrReuse(
                    request == null ? null : request.registrationId(),
                    request == null ? null : request.recipientType(),
                    request == null ? null : request.recipientText());
        }
        if (type == OwnDocumentType.APPROVED_SUBJECTS_CERTIFICATE) {
            ApprovedSubjectsCertificateResponse issued = requestApprovedSubjectsCertificate.request(
                    new RequestApprovedSubjectsCertificateRequest(approvedSubjectsEnrollmentResolver.resolve(request)));
            return new OwnDocumentResponse(CertificateDocumentType.APPROVED_SUBJECTS_CERTIFICATE, issued.id(), issued.code(),
                    issued.status(), issued.issuedAt(), issued.expiresAt(), issued.verificationUrl());
        }
        throw new DocumentOperationPendingException();
    }
}
