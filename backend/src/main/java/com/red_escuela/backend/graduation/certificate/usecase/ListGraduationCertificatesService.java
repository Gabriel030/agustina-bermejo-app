package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListGraduationCertificatesService implements ListGraduationCertificatesUseCase {

    @Override
    public Page<GraduationCertificateSummaryResponse> list(GraduationCertificateFilter filter, Pageable pageable) {
        throw new GraduationCertificateOperationPendingException();
    }
}
