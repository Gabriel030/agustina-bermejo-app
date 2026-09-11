package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplication;

public interface RequestProgramEnrollmentApplicationUseCase {
    ProgramEnrollmentApplication request(RequestProgramEnrollmentApplicationCommand command);
}
