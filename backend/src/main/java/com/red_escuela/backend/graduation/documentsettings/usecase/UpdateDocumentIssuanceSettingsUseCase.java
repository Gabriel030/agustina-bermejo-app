package com.red_escuela.backend.graduation.documentsettings.usecase;

import com.red_escuela.backend.graduation.documentsettings.dto.DocumentIssuanceSettingsResponse;
import com.red_escuela.backend.graduation.documentsettings.dto.UpdateDocumentIssuanceSettingsRequest;

public interface UpdateDocumentIssuanceSettingsUseCase {

    DocumentIssuanceSettingsResponse update(UpdateDocumentIssuanceSettingsRequest request);
}
