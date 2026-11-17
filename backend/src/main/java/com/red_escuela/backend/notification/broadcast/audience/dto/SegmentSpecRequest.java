package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentQuantifier;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SegmentSpecRequest(
        @NotNull Integer version,
        @NotNull SegmentQuantifier quantifier,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull SegmentCriterionRequest> criteria) {
}
