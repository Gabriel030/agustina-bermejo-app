package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank String newPassword) {
}
