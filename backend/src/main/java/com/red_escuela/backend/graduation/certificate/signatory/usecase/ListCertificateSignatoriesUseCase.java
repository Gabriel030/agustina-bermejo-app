package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryFilter;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListCertificateSignatoriesUseCase {

    Page<CertificateSignatorySummaryResponse> list(CertificateSignatoryFilter filter, Pageable pageable);
}
