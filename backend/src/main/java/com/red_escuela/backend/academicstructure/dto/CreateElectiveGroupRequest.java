package com.red_escuela.backend.academicstructure.dto;

import com.red_escuela.backend.shared.validation.RequestLimits;
import com.red_escuela.backend.shared.validation.SelfValidatingRequest;
import jakarta.validation.constraints.NotBlank;

public record CreateElectiveGroupRequest(@NotBlank String name, String description) implements SelfValidatingRequest {
    public CreateElectiveGroupRequest {
        RequestLimits.requireMaxLength("name", name, 255);
        RequestLimits.requireMaxLength("description", description, 500);
    }
}
