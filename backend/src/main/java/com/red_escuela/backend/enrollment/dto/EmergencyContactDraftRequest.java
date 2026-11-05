package com.red_escuela.backend.enrollment.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;

public record EmergencyContactDraftRequest(
        String name,
        String relationship,
        String phone) implements SelfValidatingRequest {
    public EmergencyContactDraftRequest {
        RequestLimits.requireMaxLength("emergencyContact.name", name, 200);
        RequestLimits.requireMaxLength("emergencyContact.relationship", relationship, 60);
        RequestLimits.requireMaxLength("emergencyContact.phone", phone, 40);
    }
}
