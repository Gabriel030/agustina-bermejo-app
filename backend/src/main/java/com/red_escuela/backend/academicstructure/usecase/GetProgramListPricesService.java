package com.red_escuela.backend.academicstructure.usecase;

import com.red_escuela.backend.academicstructure.dto.ProgramListPrices;
import com.red_escuela.backend.academicstructure.service.OfferingListPriceReader;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProgramListPricesService implements GetProgramListPricesUseCase {

    private final OfferingListPriceReader listPriceReader;

    public GetProgramListPricesService(OfferingListPriceReader listPriceReader) {
        this.listPriceReader = listPriceReader;
    }

    @Override
    public ProgramListPrices execute(Long programId) {
        return new ProgramListPrices(listPriceReader.findProgramTuition(programId).orElse(null),
                listPriceReader.findProgramEnrollmentFee(programId).orElse(null));
    }
}
