package com.red_escuela.backend.dataimport.dto;

import com.red_escuela.backend.dataimport.domain.ImportEntityType;
import java.util.List;

public record ImportRollbackPreviewResponse(boolean canRollback, List<CreatedCount> created, List<Blocker> blockers) {

    public record CreatedCount(ImportEntityType entityType, long total) {
    }

    public record Blocker(ImportEntityType entityType, String naturalKey, String reason) {
    }
}
