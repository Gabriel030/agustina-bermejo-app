package com.red_escuela.backend.enrollment.usecase.programenrollmentapplication;

import com.red_escuela.backend.enrollment.dto.ProgramEnrollmentApplicationApprovalCheckResponse;

public interface GetProgramEnrollmentApplicationApprovalCheckUseCase {
    ProgramEnrollmentApplicationApprovalCheckResponse get(Long applicationId);
}
