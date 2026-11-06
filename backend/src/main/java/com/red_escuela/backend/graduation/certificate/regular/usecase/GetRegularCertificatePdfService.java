package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.service.RegularCertificatePdfProvider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetRegularCertificatePdfService implements GetRegularCertificatePdfUseCase {

    private final RegularCertificatePdfProvider pdfProvider;

    public GetRegularCertificatePdfService(RegularCertificatePdfProvider pdfProvider) {
        this.pdfProvider = pdfProvider;
    }

    @Override
    public RegularCertificatePdf pdfOf(Long id) {
        return pdfProvider.pdfOf(id);
    }
}
