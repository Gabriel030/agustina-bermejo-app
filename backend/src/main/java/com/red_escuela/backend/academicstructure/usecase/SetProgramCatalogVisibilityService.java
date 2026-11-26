package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramResponse;
import com.red_escuela.backend.academicstructure.exception.ProgramNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ProgramRepository;
import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;
import java.time.Clock;
import java.time.LocalDateTime;

@UseCase
public class SetProgramCatalogVisibilityService implements SetProgramCatalogVisibilityUseCase {

    private final ProgramRepository programRepository;

    private final OfferingListPriceReader listPriceReader;

    private final Clock clock;

    public SetProgramCatalogVisibilityService(ProgramRepository programRepository, OfferingListPriceReader listPriceReader, Clock clock) {
        this.programRepository = programRepository;
        this.listPriceReader = listPriceReader;
        this.clock = clock;
    }

    @Override
    public ProgramResponse execute(SetCatalogVisibilityCommand command) {
        var programId = command.offeringId();
        var program = programRepository.findById(programId)
                .orElseThrow(() -> new ProgramNotFoundException(programId));
        program.changeCatalogVisibility(command.visible(), command.area(), LocalDateTime.now(clock));
        var saved = programRepository.save(program);
        return ProgramResponse.from(saved, listPriceReader.findProgramTuition(programId).orElse(null),
                listPriceReader.findProgramEnrollmentFee(programId).orElse(null));
    }
}
