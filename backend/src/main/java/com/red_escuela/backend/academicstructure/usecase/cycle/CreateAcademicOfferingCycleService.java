package com.red_escuela.backend.academicstructure.usecase.cycle;

import com.red_escuela.backend.academicstructure.dto.AcademicOfferingCycleResponse;
import com.red_escuela.backend.academicstructure.dto.CreateAcademicOfferingCycleRequest;
import com.red_escuela.backend.academicstructure.service.CreateAcademicOfferingCycleTransaction;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;

@UseCase
public class CreateAcademicOfferingCycleService implements CreateAcademicOfferingCycleUseCase {

    private final CreateAcademicOfferingCycleTransaction createAcademicOfferingCycleTransaction;

    public CreateAcademicOfferingCycleService(CreateAcademicOfferingCycleTransaction createAcademicOfferingCycleTransaction) {
        this.createAcademicOfferingCycleTransaction = createAcademicOfferingCycleTransaction;
    }

    @Override
    public AcademicOfferingCycleResponse create(CreateAcademicOfferingCycleRequest request) {
        if (request.programId() == null && request.courseId() == null) {
            throw new RequestFieldRequiredException("programId o courseId");
        }
        return createAcademicOfferingCycleTransaction.create(request.programId(), request.courseId(), request.termId());
    }
}
