package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DiscardDraftDocumentTemplateService implements DiscardDraftDocumentTemplateUseCase {

    @Override
    public void discard(DocumentTemplateType type) {
        throw new DocumentOperationPendingException();
    }
}
