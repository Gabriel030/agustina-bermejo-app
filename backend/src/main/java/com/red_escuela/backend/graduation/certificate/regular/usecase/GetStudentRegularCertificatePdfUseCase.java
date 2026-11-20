package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;

public interface GetStudentRegularCertificatePdfUseCase {

    RegularCertificatePdf pdfOf(Long certificateId);
}
