package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword) {
}
