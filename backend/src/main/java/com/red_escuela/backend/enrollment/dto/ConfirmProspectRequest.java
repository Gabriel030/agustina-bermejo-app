package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
        @NotNull @Past LocalDate birthDate,
        @Size(max = 255) String sex,
        @Size(max = 255) String nationality,
        @Valid GuardianRequest guardian,
        @Valid EmergencyContactRequest emergencyContact) {
}
