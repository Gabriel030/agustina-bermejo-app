package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.dto.IssueGraduationCertificateRequest;
import com.red_escuela.backend.graduation.certificate.specialist.GraduationCertificatePreviewer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class PreviewGraduationCertificateService implements PreviewGraduationCertificateUseCase {

    private final GraduationCertificatePreviewer previewer;

    public PreviewGraduationCertificateService(GraduationCertificatePreviewer previewer) {
        this.previewer = previewer;
    }

    @Override
    public GraduationCertificatePdf preview(IssueGraduationCertificateRequest request) {
        return previewer.preview(request.kind(), request.enrollmentId());
    }
}
