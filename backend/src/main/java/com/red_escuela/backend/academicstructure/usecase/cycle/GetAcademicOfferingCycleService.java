package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetAcademicOfferingCycleService implements GetAcademicOfferingCycleUseCase {

    private final AcademicOfferingCycleReader cycleReader;

    public GetAcademicOfferingCycleService(AcademicOfferingCycleReader cycleReader) {
        this.cycleReader = cycleReader;
    }

    @Override
    public AcademicOfferingCycleResponse get(Long cycleId) {
        return cycleReader.readCycle(cycleId);
    }
}
