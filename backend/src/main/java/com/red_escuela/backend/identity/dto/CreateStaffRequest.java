package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffRequest(
        @NotBlank String nombre, @NotBlank String apellido, @NotBlank @Email String email,
        @NotBlank String dni, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) DocumentType documentType, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String street, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String number, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String floor, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String city,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String state, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String postalCode, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String country,
        @NotBlank String role) {
}
