package com.red_escuela.backend.identity.specialist;

import com.red_escuela.backend.identity.domain.PermissionDiscrepancyDirection;

public record PermissionDiscrepancyKey(String role, String permission, PermissionDiscrepancyDirection direction) {
}
