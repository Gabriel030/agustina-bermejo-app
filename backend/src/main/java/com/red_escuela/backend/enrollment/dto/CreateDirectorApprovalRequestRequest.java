package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.entity.ExceptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDirectorApprovalRequestRequest(
        @NotNull ExceptionType type, @NotNull Long programEnrollmentId, Long subjectId, @NotBlank String justification) {
}
