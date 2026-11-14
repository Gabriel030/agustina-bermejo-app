package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmGuardianAcceptanceRequest(@NotBlank @Size(max = 200) String token) {
}
