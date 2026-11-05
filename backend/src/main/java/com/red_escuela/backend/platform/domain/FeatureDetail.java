package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.FeatureCode;
import com.red_escuela.backend.shared.module.BillingMode;

import java.time.LocalDateTime;

public record FeatureDetail(FeatureCode code, boolean enabled, BillingMode mode, long version, LocalDateTime updatedAt, ActorSummary updatedBy, String lastReason) {
}
