package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.certificate.exception.DocumentOperationPendingException;
import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class GetProcedureEligibilityService implements GetProcedureEligibilityUseCase {

    @Override
    public ProcedureEligibilityResponse eligibilityOf(StudentProcedureType type, Long enrollmentId, Long registrationId) {
        throw new DocumentOperationPendingException();
    }
}
