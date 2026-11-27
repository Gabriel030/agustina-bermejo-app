package com.red_escuela.backend.billing.usecase.cohort;

import com.red_escuela.backend.billing.cohort.service.CreateProgramCohortTransaction;
import com.red_escuela.backend.billing.dto.CreateProgramCohortRequest;
import com.red_escuela.backend.academicstructure.entity.ProgramCohort;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateProgramCohortService implements CreateProgramCohortUseCase {

    private final CreateProgramCohortTransaction createTransaction;

    public CreateProgramCohortService(CreateProgramCohortTransaction createTransaction) {
        this.createTransaction = createTransaction;
    }

    @Override
    public ProgramCohort create(Long templateId, CreateProgramCohortRequest request) {
        return createTransaction.create(templateId, request);
    }
}
