package com.red_escuela.backend.identity.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateNewsletterPreferenceRequest(@NotNull Boolean optedIn) {
}
