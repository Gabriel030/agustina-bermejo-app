package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.procedure.specialist.ExamRegistrationCertificateIssuer;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateOwnDocumentService implements GenerateOwnDocumentUseCase {

    private final ExamRegistrationCertificateIssuer examRegistrationIssuer;

    public GenerateOwnDocumentService(ExamRegistrationCertificateIssuer examRegistrationIssuer) {
        this.examRegistrationIssuer = examRegistrationIssuer;
    }

    @Override
    public OwnDocumentResponse generate(OwnDocumentType type, GenerateOwnDocumentRequest request) {
        if (type == OwnDocumentType.EXAM_REGISTRATION_CERTIFICATE) {
            return examRegistrationIssuer.issueOrReuse(
                    request == null ? null : request.registrationId(),
                    request == null ? null : request.recipientType(),
                    request == null ? null : request.recipientText());
        }
        throw new DocumentOperationPendingException();
    }
}
