package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyResetCodeRequest(@NotBlank @Email String email, @NotBlank String code) {
}
