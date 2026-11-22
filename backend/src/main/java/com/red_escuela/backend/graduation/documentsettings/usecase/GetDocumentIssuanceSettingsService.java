package com.red_escuela.backend.graduation.documentsettings.usecase;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.specialist.DocumentIssuanceSettingsFinder;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetDocumentIssuanceSettingsService implements GetDocumentIssuanceSettingsUseCase {

    private final DocumentIssuanceSettingsFinder finder;

    public GetDocumentIssuanceSettingsService(DocumentIssuanceSettingsFinder finder) {
        this.finder = finder;
    }

    @Override
    public DocumentIssuanceSettingsResponse get() {
        return finder.current();
    }
}
