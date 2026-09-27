package com.red_escuela.backend.announcement.dto;

import com.red_escuela.backend.announcement.entity.AudienceQuantifier;
import com.red_escuela.backend.announcement.entity.AudienceSegmentKey;
import com.red_escuela.backend.announcement.entity.AudienceSpec;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AudienceSpecRequest(
        @NotNull AudienceSegmentKey key,
        List<Integer> instances,
        AudienceQuantifier quantifier,
        Integer threshold) {

    public AudienceSpec toSpec() {
        String joined = instances == null || instances.isEmpty()
                ? null
                : instances.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse(null);
        return new AudienceSpec(key, joined, quantifier, threshold);
    }
}
