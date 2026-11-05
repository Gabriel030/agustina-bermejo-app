package com.red_escuela.backend.identity.usecase.institution;

import com.red_escuela.backend.identity.dto.AcademicSettingsRequest;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;

public interface UpdateAcademicSettingsUseCase {
    AcademicSettingsResponse update(AcademicSettingsRequest request);
}
