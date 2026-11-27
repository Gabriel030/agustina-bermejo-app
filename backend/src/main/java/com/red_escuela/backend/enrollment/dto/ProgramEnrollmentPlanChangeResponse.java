package com.red_escuela.backend.enrollment.dto;

import java.util.List;

public record ProgramEnrollmentPlanChangeResponse(Long programEnrollmentId, Long planId, List<Long> equivalenceIds) {
}
