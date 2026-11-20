package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduateResponse;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduatesFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ListEligibleGraduatesUseCase {

    Page<EligibleGraduateResponse> list(EligibleGraduatesFilter filter, Pageable pageable);
}
