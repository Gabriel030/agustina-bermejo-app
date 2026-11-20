package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailChangeTokenRequest(@NotBlank @Size(min = 32, max = 128) String token) {
}
