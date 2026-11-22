package com.red_escuela.backend.graduation.procedure.usecase;

import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;

public interface GetProcedureEligibilityUseCase {

    ProcedureEligibilityResponse eligibilityOf(StudentProcedureType type, Long enrollmentId, Long registrationId);
}
