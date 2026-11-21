package com.red_escuela.backend.graduation.certificate.usecase;

import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduateResponse;
import com.red_escuela.backend.graduation.certificate.dto.EligibleGraduatesFilter;
import com.red_escuela.backend.graduation.certificate.specialist.EligibleGraduateLister;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@UseCase
public class ListEligibleGraduatesService implements ListEligibleGraduatesUseCase {

    private final EligibleGraduateLister lister;

    public ListEligibleGraduatesService(EligibleGraduateLister lister) {
        this.lister = lister;
    }

    @Override
    public Page<EligibleGraduateResponse> list(EligibleGraduatesFilter filter, Pageable pageable) {
        return lister.list(filter, pageable);
    }
}
