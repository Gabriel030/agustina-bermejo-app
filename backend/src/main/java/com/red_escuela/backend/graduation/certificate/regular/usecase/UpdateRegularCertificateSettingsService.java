package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSettingsResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.UpdateRegularCertificateSettingsRequest;
import com.red_escuela.backend.graduation.certificate.regular.entity.RegularCertificateSettings;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsUpdater;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateRegularCertificateSettingsService implements UpdateRegularCertificateSettingsUseCase {

    private final RegularCertificateSettingsUpdater updater;

    public UpdateRegularCertificateSettingsService(RegularCertificateSettingsUpdater updater) {
        this.updater = updater;
    }

    @Override
    public RegularCertificateSettingsResponse update(UpdateRegularCertificateSettingsRequest request) {
        RegularCertificateSettings settings = RegularCertificateSettings.of(request.validityDays(),
                request.exceptionValidityDays(), request.evidenceRequired());
        return RegularCertificateSettingsResponses.of(updater.update(settings, request.version()));
    }
}
