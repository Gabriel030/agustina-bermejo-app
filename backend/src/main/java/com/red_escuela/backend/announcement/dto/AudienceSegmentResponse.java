package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;

public record AudienceSegmentResponse(
        AudienceSegmentKey key,
        String label,
        Integer count,
        boolean needsInstances,
        boolean needsThreshold,
        Integer instanceCount) {
}
