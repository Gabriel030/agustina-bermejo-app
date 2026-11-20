package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSettingsResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsFinder;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsFinder.RegularCertificateSettingsView;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetRegularCertificateSettingsService implements GetRegularCertificateSettingsUseCase {

    private final RegularCertificateSettingsFinder finder;

    public GetRegularCertificateSettingsService(RegularCertificateSettingsFinder finder) {
        this.finder = finder;
    }

    @Override
    public RegularCertificateSettingsResponse get() {
        return RegularCertificateSettingsResponses.of(finder.currentView());
    }
}
