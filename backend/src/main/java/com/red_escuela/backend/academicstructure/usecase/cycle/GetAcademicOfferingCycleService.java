package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.exception.CycleNotFoundException;
import com.red_escuela.backend.academicstructure.repository.AcademicOfferingCycleRepository;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@Transactional(readOnly = true)
public class GetAcademicOfferingCycleService implements GetAcademicOfferingCycleUseCase {

    private final AcademicOfferingCycleRepository cycleRepository;

    public GetAcademicOfferingCycleService(AcademicOfferingCycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    @Override
    public AcademicOfferingCycleResponse get(Long cycleId) {
        return cycleRepository.findById(cycleId)
                .map(AcademicOfferingCycleResponse::from)
                .orElseThrow(() -> new CycleNotFoundException(cycleId));
    }
}
