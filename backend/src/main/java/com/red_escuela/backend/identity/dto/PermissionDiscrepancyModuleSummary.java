package com.red_escuela.backend.identity.dto;

import java.time.Instant;

public record PermissionDiscrepancyModuleSummary(
        String module,
        long legacyAllowedGridDenied,
        long legacyDeniedGridAllowed,
        long total,
        Instant lastSeenAt) {
}
