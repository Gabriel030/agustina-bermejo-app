package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.red_escuela.backend.shared.validation.RequestLimits;

public record CreateProspectRequest(
        @NotBlank String nombre,
        @NotBlank String apellido,
        @NotBlank @Email String email,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String telefono,
        @NotNull Long institutionId) {

    public CreateProspectRequest {
        RequestLimits.requireMaxLength("nombre", nombre, 100);
        RequestLimits.requireMaxLength("apellido", apellido, 100);
        RequestLimits.requireMaxLength("email", email, 254);
        RequestLimits.requireMaxLength("telefono", telefono, 30);
    }
}
