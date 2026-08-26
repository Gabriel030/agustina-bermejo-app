package com.red_escuela.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignElectiveGroupRequest(
        @NotNull Long electiveGroupId, @NotNull @Min(0) Integer minRequired,
        @NotNull @Min(1) Integer maxAllowed) {
}
