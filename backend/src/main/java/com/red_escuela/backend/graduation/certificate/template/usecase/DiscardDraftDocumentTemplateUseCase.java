package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface DiscardDraftDocumentTemplateUseCase {

    void discard(DocumentTemplateType type);
}
