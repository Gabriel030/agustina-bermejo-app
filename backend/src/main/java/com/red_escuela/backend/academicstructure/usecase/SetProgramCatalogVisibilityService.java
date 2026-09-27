package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class SetProgramCatalogVisibilityService implements SetProgramCatalogVisibilityUseCase {

    private final ProgramRepository programRepository;

    public SetProgramCatalogVisibilityService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    @Override
    public ProgramResponse execute(Long programId, boolean visible) {
        var program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        program.setVisibleInCatalog(visible);
        return ProgramResponse.from(programRepository.save(program));
    }
}
