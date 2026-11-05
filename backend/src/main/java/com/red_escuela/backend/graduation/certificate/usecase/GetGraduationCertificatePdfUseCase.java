package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;

public interface GetGraduationCertificatePdfUseCase {

    GraduationCertificatePdf pdfOf(Long id);
}
