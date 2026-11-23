package com.red_escuela.backend.graduation.certificate.exam.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificatePdf;
import com.red_escuela.backend.graduation.certificate.exam.specialist.ExamCertificatePdfProvider;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetExamCertificatePdfService implements GetExamCertificatePdfUseCase {

    private final ExamCertificatePdfProvider delegate;

    public GetExamCertificatePdfService(ExamCertificatePdfProvider delegate) {
        this.delegate = delegate;
    }

    @Override
    public GraduationCertificatePdf pdfOf(Long id) {
        return delegate.pdfOf(id);
    }
}
