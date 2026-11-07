package com.red_escuela.backend.platform.usecase.settings;

import com.red_escuela.backend.platform.domain.InstituteSettings;
import com.red_escuela.backend.platform.specialist.InstituteSettingsReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetInstituteSettingsService implements GetInstituteSettingsUseCase {

    private final InstituteSettingsReader settingsReader;

    public GetInstituteSettingsService(InstituteSettingsReader settingsReader) {
        this.settingsReader = settingsReader;
    }

    @Override
    public InstituteSettings read() {
        return settingsReader.readAll();
    }
}
