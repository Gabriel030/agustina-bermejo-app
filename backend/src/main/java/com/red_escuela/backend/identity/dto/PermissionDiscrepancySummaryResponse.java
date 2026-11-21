package com.red_escuela.backend.identity.dto;

import java.util.List;

public record PermissionDiscrepancySummaryResponse(List<PermissionDiscrepancyModuleSummary> modules) {
}
