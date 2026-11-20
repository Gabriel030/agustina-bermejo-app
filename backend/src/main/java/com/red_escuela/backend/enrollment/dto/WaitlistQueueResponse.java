package com.red_escuela.backend.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.util.List;

public record WaitlistQueueResponse(WaitlistKind kind, Long id, String name, String termName,
                                    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer capacity,
                                    long occupied,
                                    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Long free,
                                    long waiting,
                                    @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) LocalDate nextExpiry,
                                    List<WaitlistEntryResponse> entries) {

    public static WaitlistQueueResponse of(WaitlistQueueSummaryResponse summary, List<WaitlistEntryResponse> entries) {
        return new WaitlistQueueResponse(summary.kind(), summary.id(), summary.name(), summary.termName(), summary.capacity(),
                summary.occupied(), summary.free(), summary.waiting(), summary.nextExpiry(), entries);
    }
}
