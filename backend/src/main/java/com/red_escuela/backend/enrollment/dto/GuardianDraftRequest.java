package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;

public record GuardianDraftRequest(
        String fullName,
        String documentNumber,
        String relationship,
        String phone,
        String email) implements SelfValidatingRequest {
    public GuardianDraftRequest {
        RequestLimits.requireMaxLength("guardian.fullName", fullName, 200);
        RequestLimits.requireMaxLength("guardian.documentNumber", documentNumber, 20);
        RequestLimits.requireMaxLength("guardian.relationship", relationship, 60);
        RequestLimits.requireMaxLength("guardian.phone", phone, 40);
        RequestLimits.requireMaxLength("guardian.email", email, 255);
    }
}
