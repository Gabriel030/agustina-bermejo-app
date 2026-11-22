package com.red_escuela.backend.graduation.certificate.template.usecase;

import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateResponse;
import com.red_escuela.backend.graduation.certificate.template.dto.DocumentTemplateType;

public interface GetDocumentTemplateUseCase {

    DocumentTemplateResponse get(DocumentTemplateType type);
}
