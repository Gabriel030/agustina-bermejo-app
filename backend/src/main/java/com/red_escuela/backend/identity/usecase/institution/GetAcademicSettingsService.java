package com.red_escuela.backend.identity.usecase.institution;

import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetAcademicSettingsService implements GetAcademicSettingsUseCase {

    private final InstitutionAcademicSettingsReader reader;

    public GetAcademicSettingsService(InstitutionAcademicSettingsReader reader) {
        this.reader = reader;
    }

    @Override
    public AcademicSettingsResponse get() {
        return AcademicSettingsResponse.from(reader.read());
    }
}
