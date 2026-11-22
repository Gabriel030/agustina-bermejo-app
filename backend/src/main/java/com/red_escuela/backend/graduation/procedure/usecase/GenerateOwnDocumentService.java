package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GenerateOwnDocumentService implements GenerateOwnDocumentUseCase {

    @Override
    public OwnDocumentResponse generate(OwnDocumentType type, GenerateOwnDocumentRequest request) {
        throw new DocumentOperationPendingException();
    }
}
