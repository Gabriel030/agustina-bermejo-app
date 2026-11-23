package com.red_escuela.backend.graduation.procedure.specialist;

import com.red_escuela.backend.graduation.procedure.dto.ProcedureEligibilityResponse;
import com.red_escuela.backend.graduation.procedure.dto.StudentProcedureType;

public interface ProcedureEligibilityStrategy {

    StudentProcedureType type();

    ProcedureEligibilityResponse eligibilityOf(Long studentId, Long enrollmentId, Long registrationId);
}
