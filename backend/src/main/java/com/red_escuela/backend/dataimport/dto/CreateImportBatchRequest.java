package com.red_escuela.backend.dataimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateImportBatchRequest(@NotBlank @Size(max = 120) String name) {
}
