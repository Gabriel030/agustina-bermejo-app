package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;

public interface GetRegularCertificatePdfUseCase {

    RegularCertificatePdf pdfOf(Long id);
}
