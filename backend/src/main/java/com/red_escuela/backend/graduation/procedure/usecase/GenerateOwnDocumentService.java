package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.graduation.procedure.specialist.OwnRegularDocumentIssuer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateOwnDocumentService implements GenerateOwnDocumentUseCase {

    private final OwnRegularDocumentIssuer regularIssuer;

    public GenerateOwnDocumentService(OwnRegularDocumentIssuer regularIssuer) {
        this.regularIssuer = regularIssuer;
    }

    @Override
    public OwnDocumentResponse generate(OwnDocumentType type, GenerateOwnDocumentRequest request) {
        if (type == OwnDocumentType.REGULAR_STUDENT_CERTIFICATE) {
            return regularIssuer.issue(request);
        }
        throw new DocumentOperationPendingException();
    }
}
