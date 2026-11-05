package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record SaveProspectDraftRequest(
        String dni,
        DocumentType documentType,
        String street,
        String number,
        String floor,
        String city,
        String state,
        String postalCode,
        String country,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate birthDate,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String sex,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String nationality,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) GuardianDraftRequest guardian,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) EmergencyContactDraftRequest emergencyContact) implements SelfValidatingRequest {
    public SaveProspectDraftRequest {
        RequestLimits.requireMaxLength("dni", dni, 255);
        RequestLimits.requireMaxLength("street", street, 255);
        RequestLimits.requireMaxLength("number", number, 255);
        RequestLimits.requireMaxLength("floor", floor, 255);
        RequestLimits.requireMaxLength("city", city, 255);
        RequestLimits.requireMaxLength("state", state, 255);
        RequestLimits.requireMaxLength("postalCode", postalCode, 255);
        RequestLimits.requireMaxLength("country", country, 255);
        RequestLimits.requireMaxLength("sex", sex, 255);
        RequestLimits.requireMaxLength("nationality", nationality, 255);
    }
}
