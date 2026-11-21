package com.red_escuela.backend.identity.dto;

import java.time.Instant;

public record PermissionDiscrepancyResponse(
        String role,
        String module,
        String permissionCode,
        long count,
        Instant lastSeenAt) {
}
