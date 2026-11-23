package com.red_escuela.backend.graduation.documentsettings.usecase;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.dto.UpdateDocumentIssuanceSettingsRequest;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsUpdater;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateDocumentIssuanceSettingsService implements UpdateDocumentIssuanceSettingsUseCase {

    private final DocumentIssuanceSettingsUpdater updater;

    public UpdateDocumentIssuanceSettingsService(DocumentIssuanceSettingsUpdater updater) {
        this.updater = updater;
    }

    @Override
    public DocumentIssuanceSettingsResponse update(UpdateDocumentIssuanceSettingsRequest request) {
        return updater.update(request);
    }
}
