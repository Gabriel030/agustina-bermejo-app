package com.red_escuela.backend.graduation.documentsettings.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDocumentIssuanceSettingsService implements GetDocumentIssuanceSettingsUseCase {

    @Override
    public DocumentIssuanceSettingsResponse get() {
        throw new DocumentOperationPendingException();
    }
}
