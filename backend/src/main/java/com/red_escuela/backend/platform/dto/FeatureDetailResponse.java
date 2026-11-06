package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.FeatureDetail;
import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.module.BillingMode;

import java.time.LocalDateTime;

public record FeatureDetailResponse(FeatureCode code, boolean enabled, BillingMode mode, long version, LocalDateTime updatedAt, ActorSummaryResponse updatedBy, String lastReason) {

    public static FeatureDetailResponse of(FeatureDetail detail) {
        return new FeatureDetailResponse(detail.code(), detail.enabled(), detail.mode(), detail.version(), detail.updatedAt(),
                ActorSummaryResponse.of(detail.updatedBy()), detail.lastReason());
    }
}
