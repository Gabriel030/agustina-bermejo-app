package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewGraduationCertificateService implements PreviewGraduationCertificateUseCase {

    @Override
    public GraduationCertificatePdf preview(IssueGraduationCertificateRequest request) {
        throw new GraduationCertificateOperationPendingException();
    }
}
