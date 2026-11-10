package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;

public interface GetProgramEnrollmentApplicationForReviewUseCase {
    ProgramEnrollmentApplicationResponse get(Long id);
}
