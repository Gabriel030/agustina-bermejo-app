package com.red_escuela.backend.graduation.certificate.signatory.usecase;

import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatoryFilter;
import com.red_escuela.backend.graduation.certificate.signatory.dto.CertificateSignatorySummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListCertificateSignatoriesService implements ListCertificateSignatoriesUseCase {

    @Override
    public Page<CertificateSignatorySummaryResponse> list(CertificateSignatoryFilter filter, Pageable pageable) {
        throw new GraduationCertificateOperationPendingException();
    }
}
