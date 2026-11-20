package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;

public interface PreviewGraduationCertificateUseCase {

    GraduationCertificatePdf preview(IssueGraduationCertificateRequest request);
}
