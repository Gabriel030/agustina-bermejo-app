package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;

public interface GetExamCertificatePdfUseCase {

    GraduationCertificatePdf pdfOf(Long id);
}
