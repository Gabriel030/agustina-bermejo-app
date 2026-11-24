package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VoidSubjectEquivalenceRequest(@Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String reason) {
}
