package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SetProgramCatalogVisibilityService implements SetProgramCatalogVisibilityUseCase {

    private final ProgramRepository programRepository;

    private final OfferingListPriceReader listPriceReader;

    public SetProgramCatalogVisibilityService(ProgramRepository programRepository, OfferingListPriceReader listPriceReader) {
        this.programRepository = programRepository;
        this.listPriceReader = listPriceReader;
    }

    @Override
    public ProgramResponse execute(Long programId, boolean visible) {
        var program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        program.setVisibleInCatalog(visible);
        var saved = programRepository.save(program);
        return ProgramResponse.from(saved, listPriceReader.findProgramTuition(programId).orElse(null),
                listPriceReader.findProgramEnrollmentFee(programId).orElse(null));
    }
}
