package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateFilter;
import com.red_escuela.backend.graduation.certificate.dto.GraduationCertificateSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListGraduationCertificatesUseCase {

    Page<GraduationCertificateSummaryResponse> list(GraduationCertificateFilter filter, Pageable pageable);
}
