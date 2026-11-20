package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateSettingsResponse;
import com.red_escuela.backend.graduation.certificate.regular.dto.UpdateRegularCertificateSettingsRequest;

public interface UpdateRegularCertificateSettingsUseCase {

    RegularCertificateSettingsResponse update(UpdateRegularCertificateSettingsRequest request);
}
