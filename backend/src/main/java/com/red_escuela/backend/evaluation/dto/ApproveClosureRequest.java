package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveClosureRequest(
        @NotBlank @Size(max = 50) String book,
        @NotBlank @Size(max = 50) String folio,
        boolean closeWithoutTeacherConfirmation) {
}
