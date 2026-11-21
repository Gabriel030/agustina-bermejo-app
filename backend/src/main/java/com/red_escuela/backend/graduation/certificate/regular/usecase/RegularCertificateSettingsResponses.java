package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSettingsResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateSettingsFinder.RegularCertificateSettingsView;

final class RegularCertificateSettingsResponses {

    private RegularCertificateSettingsResponses() {
    }

    static RegularCertificateSettingsResponse of(RegularCertificateSettingsView view) {
        return new RegularCertificateSettingsResponse(view.settings().validityDays().value(),
                view.settings().exceptionValidityDays().value(), view.settings().evidenceRequired(), view.version());
    }
}
