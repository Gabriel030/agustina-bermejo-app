package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
public record UpdateElectiveGroupRequest(String name, String description) implements SelfValidatingRequest {
    public UpdateElectiveGroupRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("description", description, 500);
    }
}
