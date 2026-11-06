package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.service.GraduationCertificatePdfProvider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetGraduationCertificatePdfService implements GetGraduationCertificatePdfUseCase {

    private final GraduationCertificatePdfProvider pdfProvider;

    public GetGraduationCertificatePdfService(GraduationCertificatePdfProvider pdfProvider) {
        this.pdfProvider = pdfProvider;
    }

    @Override
    public GraduationCertificatePdf pdfOf(Long id) {
        return pdfProvider.pdfOf(id);
    }
}
