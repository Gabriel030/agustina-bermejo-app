package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactivateStudentRequest(@NotBlank @Size(min = 5, max = 500) String reason) {
}
