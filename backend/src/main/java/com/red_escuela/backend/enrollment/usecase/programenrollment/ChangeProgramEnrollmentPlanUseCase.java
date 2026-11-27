package com.red_escuela.backend.enrollment.usecase.programenrollment;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentPlanChangeResponse;

public interface ChangeProgramEnrollmentPlanUseCase {
    ProgramEnrollmentPlanChangeResponse change(ChangeProgramEnrollmentPlanCommand command);
}
