package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

public record UpdateStaffRequest(
        @Email @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String email, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String telefono, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String street, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String number, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String floor,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String city, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String state, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String postalCode, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String country) {
}
