package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDocumentTemplateService implements GetDocumentTemplateUseCase {

    @Override
    public DocumentTemplateResponse get(DocumentTemplateType type) {
        throw new DocumentOperationPendingException();
    }
}
