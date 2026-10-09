package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateStaffRequest(
        @NotBlank String nombre, @NotBlank String apellido, @NotBlank @Email String email,
        @NotBlank String dni, DocumentType documentType, String street, String number, String floor, String city,
        String state, String postalCode, String country,
        @NotBlank String role) {
}
