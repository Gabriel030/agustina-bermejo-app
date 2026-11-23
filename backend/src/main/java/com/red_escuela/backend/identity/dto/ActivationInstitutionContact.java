package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ActivationInstitutionContact(@Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String name, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String email, @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String phoneNumber) {
}
