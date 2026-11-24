package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(min = 1, max = 255) String username,
        @NotBlank String password) {
}
