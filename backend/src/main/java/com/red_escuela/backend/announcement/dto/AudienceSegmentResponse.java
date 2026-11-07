package com.red_escuela.backend.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;

public record AudienceSegmentResponse(
        AudienceSegmentKey key,
        String label,
        @Schema(nullable = true) Integer count,
        boolean needsInstances,
        boolean needsThreshold,
        @Schema(nullable = true) Integer instanceCount) {
}
