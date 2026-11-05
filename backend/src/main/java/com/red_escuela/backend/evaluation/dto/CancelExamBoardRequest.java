package com.red_escuela.backend.evaluation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelExamBoardRequest(@NotBlank @Size(max = 255) String reason) {
}
