package com.red_escuela.backend.announcement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.announcement.entity.AudienceQuantifier;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AudienceSpecRequest(
        @NotNull AudienceSegmentKey key,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) List<Integer> instances,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) AudienceQuantifier quantifier,
        @Schema(nullable = true, requiredMode = Schema.RequiredMode.NOT_REQUIRED) Integer threshold) {

    public AudienceSpec toSpec() {
        String joined = instances == null || instances.isEmpty()
                ? null
                : instances.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
        return new AudienceSpec(key, joined, quantifier, threshold);
    }
}
