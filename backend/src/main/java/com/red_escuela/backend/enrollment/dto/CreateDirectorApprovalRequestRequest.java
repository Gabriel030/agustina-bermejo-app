package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.enrollment.entity.ExceptionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateDirectorApprovalRequestRequest(
        @NotNull ExceptionType type, @NotNull Long programEnrollmentId, Long subjectId,
        @NotBlank @Size(max = 1000) String justification) {
}
