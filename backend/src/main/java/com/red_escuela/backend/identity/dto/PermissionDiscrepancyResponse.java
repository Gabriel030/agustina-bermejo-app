package com.red_escuela.backend.identity.dto;

import com.red_escuela.backend.identity.domain.PermissionDiscrepancyDirection;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

public record PermissionDiscrepancyResponse(
        String role,
        String module,
        String permissionCode,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) PermissionDiscrepancyDirection direction,
        long count,
        Instant lastSeenAt) {
}
