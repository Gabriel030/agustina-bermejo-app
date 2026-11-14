package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank @Email String email,
        @NotBlank String username,
        @NotBlank String password,
        @NotNull Long institutionId,
        Boolean acceptedTerms,
        Boolean acceptedPrivacy,
        @Past LocalDate birthDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) @Size(max = 255) String guardianName,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) @Size(max = 255) @Email String guardianEmail,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) @Size(max = 60) String guardianRelationship,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, nullable = true) Boolean acceptedNewsletter) {
}
