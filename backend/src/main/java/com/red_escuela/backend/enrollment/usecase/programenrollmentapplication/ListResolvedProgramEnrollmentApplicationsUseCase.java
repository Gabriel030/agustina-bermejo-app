package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;
import com.red_escuela.backend.enrollment.entity.ProgramEnrollmentApplicationStatus;

public interface ListResolvedProgramEnrollmentApplicationsUseCase {
    ProgramEnrollmentApplicationPage list(ProgramEnrollmentApplicationStatus status, String search, int page, int size);
}
