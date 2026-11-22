package com.red_escuela.backend.graduation.documentsettings.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.dto.UpdateDocumentIssuanceSettingsRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateDocumentIssuanceSettingsService implements UpdateDocumentIssuanceSettingsUseCase {

    @Override
    public DocumentIssuanceSettingsResponse update(UpdateDocumentIssuanceSettingsRequest request) {
        throw new DocumentOperationPendingException();
    }
}
