package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateElectiveGroupRequest(@NotBlank String name, String description) {
}
