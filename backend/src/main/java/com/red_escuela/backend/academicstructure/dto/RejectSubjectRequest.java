package com.red_escuela.backend.academicstructure.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectSubjectRequest(@NotBlank @Size(max = 500) String reason) {
}
