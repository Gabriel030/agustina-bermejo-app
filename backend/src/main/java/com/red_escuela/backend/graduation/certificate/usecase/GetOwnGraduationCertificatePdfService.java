package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnGraduationCertificatePdfService implements GetOwnGraduationCertificatePdfUseCase {

    @Override
    public GraduationCertificatePdf pdfOf(Long id) {
        throw new GraduationCertificateOperationPendingException();
    }
}
