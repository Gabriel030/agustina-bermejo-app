package com.red_escuela.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(@NotBlank String resetToken, @NotBlank String newPassword) {
}
