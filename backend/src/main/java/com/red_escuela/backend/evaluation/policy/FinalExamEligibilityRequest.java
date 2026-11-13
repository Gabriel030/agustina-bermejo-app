package com.red_escuela.backend.evaluation.policy;

import com.red_escuela.backend.enrollment.api.FinalExamEligibilityResponse;

public record FinalExamEligibilityRequest(Long enrollmentId, FinalExamEligibilityResponse eligibility) {
}
