package com.red_escuela.backend.graduation.certificate.regular.usecase;

import com.red_escuela.backend.graduation.certificate.regular.dto.RegularCertificatePdf;
import com.red_escuela.backend.graduation.certificate.regular.specialist.StudentRegularCertificateDownloader;
import com.red_escuela.backend.identity.specialist.CurrentStudentService;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetStudentRegularCertificatePdfService implements GetStudentRegularCertificatePdfUseCase {

    private final StudentRegularCertificateDownloader downloader;
    private final CurrentStudentService currentStudentService;

    public GetStudentRegularCertificatePdfService(StudentRegularCertificateDownloader downloader, CurrentStudentService currentStudentService) {
        this.downloader = downloader;
        this.currentStudentService = currentStudentService;
    }

    @Override
    public RegularCertificatePdf pdfOf(Long certificateId) {
        return downloader.pdfOf(certificateId, currentStudentService.resolveCurrentStudent().getId());
    }
}
