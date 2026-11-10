package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationResponse;

import java.util.List;

public interface ListPendingProgramEnrollmentApplicationsForReviewUseCase {
    List<ProgramEnrollmentApplicationResponse> list();
}
