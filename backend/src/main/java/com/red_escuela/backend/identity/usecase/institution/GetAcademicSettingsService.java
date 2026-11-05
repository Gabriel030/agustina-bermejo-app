package com.red_escuela.backend.identity.usecase.institution;

import com.red_escuela.backend.identity.service.InstitutionAcademicSettingsFinder;
import com.red_escuela.backend.identity.dto.AcademicSettingsResponse;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetAcademicSettingsService implements GetAcademicSettingsUseCase {

    private final InstitutionAcademicSettingsFinder finder;

    public GetAcademicSettingsService(InstitutionAcademicSettingsFinder finder) {
        this.finder = finder;
    }

    @Override
    public AcademicSettingsResponse get() {
        return AcademicSettingsResponse.from(finder.find());
    }
}
