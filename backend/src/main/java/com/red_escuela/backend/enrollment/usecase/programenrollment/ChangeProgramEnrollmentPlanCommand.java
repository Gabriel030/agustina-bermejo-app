package com.red_escuela.backend.enrollment.usecase.programenrollment;

import com.red_escuela.backend.enrollment.dto.ChangeProgramEnrollmentPlanRequest;

public record ChangeProgramEnrollmentPlanCommand(Long programEnrollmentId, ChangeProgramEnrollmentPlanRequest request) {
}
