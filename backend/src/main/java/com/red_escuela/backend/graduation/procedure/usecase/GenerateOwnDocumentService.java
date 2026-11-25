package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.graduation.procedure.specialist.ApprovedSubjectsOwnDocumentIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificateIssuer;
import com.red_escuela.backend.graduation.procedure.specialist.OwnRegularDocumentIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateOwnDocumentService implements GenerateOwnDocumentUseCase {

    private final OwnRegularDocumentIssuer regularIssuer;
    private final ExamRegistrationCertificateIssuer examRegistrationIssuer;

    private final ApprovedSubjectsOwnDocumentIssuer approvedSubjectsIssuer;

    public GenerateOwnDocumentService(OwnRegularDocumentIssuer regularIssuer, ExamRegistrationCertificateIssuer examRegistrationIssuer,
                                      ApprovedSubjectsOwnDocumentIssuer approvedSubjectsIssuer) {
        this.regularIssuer = regularIssuer;
        this.examRegistrationIssuer = examRegistrationIssuer;
        this.approvedSubjectsIssuer = approvedSubjectsIssuer;
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
            return approvedSubjectsIssuer.issue(request);
        }
        throw new DocumentOperationPendingException();
    }
}
