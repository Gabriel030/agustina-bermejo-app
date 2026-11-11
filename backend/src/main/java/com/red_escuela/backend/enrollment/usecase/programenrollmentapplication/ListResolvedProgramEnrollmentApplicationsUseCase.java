package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationPage;

public interface ListResolvedProgramEnrollmentApplicationsUseCase {
    ProgramEnrollmentApplicationPage list(String status, String search, int page, int size);
}
