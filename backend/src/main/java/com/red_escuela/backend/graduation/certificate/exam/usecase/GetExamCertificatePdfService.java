package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetExamCertificatePdfService implements GetExamCertificatePdfUseCase {

    @Override
    public GraduationCertificatePdf pdfOf(Long id) {
        throw new DocumentOperationPendingException();
    }
}
