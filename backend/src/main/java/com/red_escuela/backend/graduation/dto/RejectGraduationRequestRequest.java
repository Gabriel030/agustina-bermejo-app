package com.red_escuela.backend.graduation.dto;

import jakarta.validation.constraints.NotBlank;

public record RejectGraduationRequestRequest(@NotBlank String motivo) {
}
