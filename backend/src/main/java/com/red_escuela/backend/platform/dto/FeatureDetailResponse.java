package com.red_escuela.backend.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.platform.domain.FeatureDetail;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.module.BillingMode;

import java.time.LocalDateTime;

public record FeatureDetailResponse(FeatureCode code, boolean enabled, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL) BillingMode mode, long version, LocalDateTime updatedAt, ActorSummaryResponse updatedBy, String lastReason) {

    public static FeatureDetailResponse of(FeatureDetail detail) {
        return new FeatureDetailResponse(detail.code(), detail.enabled(), detail.mode(), detail.version(), detail.updatedAt(),
                ActorSummaryResponse.of(detail.updatedBy()), detail.lastReason());
    }
}
