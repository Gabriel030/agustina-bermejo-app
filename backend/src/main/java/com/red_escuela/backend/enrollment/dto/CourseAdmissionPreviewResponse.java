package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.Money;

public record CourseAdmissionPreviewResponse(Long courseId, boolean requiresPayment, Money amount) {
}
