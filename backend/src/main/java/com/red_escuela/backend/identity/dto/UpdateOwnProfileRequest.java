package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;

public record UpdateOwnProfileRequest(
        @Email String email, String telefono, String sexo, String dni, DocumentType documentType, String street, String number, String floor,
        String city, String state, String postalCode, String country,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String currentPassword) {

    public UpdateOwnProfileRequest(String email, String telefono, String sexo, String dni, DocumentType documentType,
                                   String street, String number, String floor, String city, String state,
                                   String postalCode, String country) {
        this(email, telefono, sexo, dni, documentType, street, number, floor, city, state, postalCode, country, null);
    }
}
