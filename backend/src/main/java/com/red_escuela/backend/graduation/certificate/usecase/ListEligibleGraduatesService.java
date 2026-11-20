package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.exception.GraduationCertificateOperationPendingException;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduateResponse;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduatesFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ListEligibleGraduatesService implements ListEligibleGraduatesUseCase {

    @Override
    public Page<EligibleGraduateResponse> list(EligibleGraduatesFilter filter, Pageable pageable) {
        throw new GraduationCertificateOperationPendingException();
    }
}
