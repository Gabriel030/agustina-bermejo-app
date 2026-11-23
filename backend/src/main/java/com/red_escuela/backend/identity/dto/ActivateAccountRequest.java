package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record ActivateAccountRequest(@NotBlank String token, @NotBlank String newPassword) {
}
