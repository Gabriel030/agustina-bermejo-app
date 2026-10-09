package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import jakarta.validation.constraints.Email;

public record UpdateOwnProfileRequest(
        @Email String email, String telefono, String sexo, String dni, DocumentType documentType, String street, String number, String floor,
        String city, String state, String postalCode, String country) {
}
