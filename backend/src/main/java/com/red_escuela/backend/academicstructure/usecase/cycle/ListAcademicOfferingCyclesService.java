package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.entity.AcademicOfferingCycleStatus;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class ListAcademicOfferingCyclesService implements ListAcademicOfferingCyclesUseCase {

    private final AcademicOfferingCycleReader cycleReader;

    public ListAcademicOfferingCyclesService(AcademicOfferingCycleReader cycleReader) {
        this.cycleReader = cycleReader;
    }

    @Override
    public List<AcademicOfferingCycleResponse> list(Long programId, Long courseId, AcademicOfferingCycleStatus status) {
        return cycleReader.listCycles(programId, courseId, status);
    }
}
