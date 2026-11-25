package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record UpdateStudentRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) DocumentType documentType,
        @NotBlank @Size(max = 30) String documentNumber,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Past LocalDate birthDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 20) String sex,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 100) String nationality,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 50) String phone,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Email String email) {
}
