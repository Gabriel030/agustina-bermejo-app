package com.red_escuela.backend.identity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ActivationTokenStatusRequest(@Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) String token) {
}
