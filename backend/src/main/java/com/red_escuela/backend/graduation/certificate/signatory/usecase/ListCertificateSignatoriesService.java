package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryFilter;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import com.red_escuela.backend.graduation.certificate.signatory.specialist.CertificateSignatoryReader;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListCertificateSignatoriesService implements ListCertificateSignatoriesUseCase {

    private final CertificateSignatoryReader specialist;

    public ListCertificateSignatoriesService(CertificateSignatoryReader specialist) {
        this.specialist = specialist;
    }

    @Override
    public Page<CertificateSignatorySummaryResponse> list(CertificateSignatoryFilter filter, Pageable pageable) {
        return specialist.list(filter, pageable);
    }
}
