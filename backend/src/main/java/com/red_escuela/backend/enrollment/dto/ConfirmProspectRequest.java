package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ConfirmProspectRequest(
        @NotBlank String dni,
        DocumentType documentType,
        @NotBlank String street,
        String number,
        String floor,
        @NotBlank String city,
        String state,
        String postalCode,
        String country,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Past LocalDate birthDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 255) String sex,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Size(max = 255) String nationality,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid GuardianRequest guardian,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @Valid EmergencyContactRequest emergencyContact) {
}
