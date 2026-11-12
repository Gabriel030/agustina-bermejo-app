package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.shared.domain.DocumentType;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.validation.RequestLimits;
import jakarta.validation.constraints.Email;

public record UpdateOwnProfileRequest(
        @Email String email, String telefono, String sexo, String dni, DocumentType documentType, String street, String number, String floor,
        String city, String state, String postalCode, String country,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String currentPassword) {

    public UpdateOwnProfileRequest {
        RequestLimits.requireMaxLength("email", email, 254);
        RequestLimits.requireMaxLength("telefono", telefono, 30);
        RequestLimits.requireMaxLength("sexo", sexo, 20);
        RequestLimits.requireMaxLength("dni", dni, 20);
        RequestLimits.requireMaxLength("street", street, 200);
        RequestLimits.requireMaxLength("number", number, 20);
        RequestLimits.requireMaxLength("floor", floor, 20);
        RequestLimits.requireMaxLength("city", city, 100);
        RequestLimits.requireMaxLength("state", state, 100);
        RequestLimits.requireMaxLength("postalCode", postalCode, 20);
        RequestLimits.requireMaxLength("country", country, 100);
        RequestLimits.requireMaxLength("currentPassword", currentPassword, 128);
    }

    public UpdateOwnProfileRequest(String email, String telefono, String sexo, String dni, DocumentType documentType,
                                   String street, String number, String floor, String city, String state,
                                   String postalCode, String country) {
        this(email, telefono, sexo, dni, documentType, street, number, floor, city, state, postalCode, country, null);
    }
}
