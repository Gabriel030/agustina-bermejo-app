package com.red_escuela.backend.enrollment.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectProgramEnrollmentApplicationRequest(@NotBlank String reason) {
}
