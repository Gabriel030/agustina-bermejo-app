package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.CreateCycleSectionRequest;
import com.red_escuela.backend.academicstructure.service.CreateCycleSectionTransaction;
import com.red_escuela.backend.shared.UseCase;

import java.time.LocalDateTime;

@UseCase
public class CreateCycleSectionService implements CreateCycleSectionUseCase {

    private final CreateCycleSectionTransaction createCycleSectionTransaction;

    public CreateCycleSectionService(CreateCycleSectionTransaction createCycleSectionTransaction) {
        this.createCycleSectionTransaction = createCycleSectionTransaction;
    }

    @Override
    public Long createSection(Long cycleId, CreateCycleSectionRequest request) {
        return createCycleSectionTransaction.createSection(cycleId, request.name(), request.subjectId(),
                request.courseSubjectId(), request.capacity(), request.slots(), request.maxAbsencePercent(),
                request.shift(), request.description(), LocalDateTime.now());
    }
}
