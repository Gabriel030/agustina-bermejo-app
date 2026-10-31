package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CycleSectionResponse;
import com.red_escuela.backend.academicstructure.service.AcademicOfferingCycleReader;
import com.red_escuela.backend.shared.UseCase;

import java.util.List;

@UseCase
public class GetCycleSectionsService implements GetCycleSectionsUseCase {

    private final AcademicOfferingCycleReader cycleReader;

    public GetCycleSectionsService(AcademicOfferingCycleReader cycleReader) {
        this.cycleReader = cycleReader;
    }

    @Override
    public List<CycleSectionResponse> getSections(Long cycleId) {
        return cycleReader.listCycleSections(cycleId);
    }
}
