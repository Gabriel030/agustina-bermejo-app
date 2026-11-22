package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.procedure.dto.GenerateOwnDocumentRequest;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentResponse;
import com.red_escuela.backend.graduation.procedure.dto.OwnDocumentType;

public interface GenerateOwnDocumentUseCase {

    OwnDocumentResponse generate(OwnDocumentType type, GenerateOwnDocumentRequest request);
}
