package com.red_escuela.backend.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AccountCreationConsentChoice(@NotBlank String category, @NotNull Boolean granted) {
}
