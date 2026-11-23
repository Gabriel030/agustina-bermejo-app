package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateIntegrityBrokenException;
import com.red_escuela.backend.graduation.certificate.specialist.OwnGraduationCertificateDownloader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetOwnGraduationCertificatePdfService implements GetOwnGraduationCertificatePdfUseCase {

    private final OwnGraduationCertificateDownloader downloader;

    public GetOwnGraduationCertificatePdfService(OwnGraduationCertificateDownloader downloader) {
        this.downloader = downloader;
    }

    @Override
    public GraduationCertificatePdf pdfOf(Long id) {
        return downloader.download(id).orElseThrow(() -> new GraduationCertificateIntegrityBrokenException(id));
    }
}
