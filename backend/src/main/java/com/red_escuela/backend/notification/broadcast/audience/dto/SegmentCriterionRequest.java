package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SegmentCriterionRequest(@NotNull SegmentKey key, @Size(max = 100) String value) {
}
