package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record GoogleRegisterRequest(@NotBlank String idToken, @NotNull Long institutionId) {
}
