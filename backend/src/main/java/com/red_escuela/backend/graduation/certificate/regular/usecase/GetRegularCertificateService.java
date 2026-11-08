package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificateResponse;
import com.red_escuela.backend.graduation.certificate.regular.specialist.RegularCertificateReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetRegularCertificateService implements GetRegularCertificateUseCase {

    private final RegularCertificateReader reader;

    public GetRegularCertificateService(RegularCertificateReader reader) {
        this.reader = reader;
    }

    @Override
    public RegularCertificateResponse get(Long id) {
        return reader.get(id);
    }
}
