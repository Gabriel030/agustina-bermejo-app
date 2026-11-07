package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import com.red_escuela.backend.shared.domain.DocumentType;
import java.time.LocalDate;

public record SaveProspectDraftRequest(
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String dni,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) DocumentType documentType,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String street,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String number,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String floor,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String city,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String state,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String postalCode,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String country,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate birthDate,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String sex,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String nationality,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) GuardianDraftRequest guardian,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) EmergencyContactDraftRequest emergencyContact) implements SelfValidatingRequest {
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
