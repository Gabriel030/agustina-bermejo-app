package com.red_escuela.backend.dataimport.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmImportActionRequest(@NotBlank @Size(max = 20) String confirmation) {
}
