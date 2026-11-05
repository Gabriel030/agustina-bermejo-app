package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
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
        LocalDate birthDate,
        String sex,
        String nationality,
        GuardianDraftRequest guardian,
        EmergencyContactDraftRequest emergencyContact) implements SelfValidatingRequest {
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
