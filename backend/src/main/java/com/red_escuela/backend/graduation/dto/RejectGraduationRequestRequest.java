package com.red_escuela.backend.graduation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectGraduationRequestRequest(@NotBlank @Size(max = 500) String motivo) {
}
