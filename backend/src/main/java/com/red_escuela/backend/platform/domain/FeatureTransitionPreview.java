package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.domain.BillingShutdownImpact;
import com.red_escuela.backend.platform.model.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.LocalDateTime;

public record FeatureTransitionPreview(
        FeatureCode code,
        BillingMode currentMode,
        BillingMode targetMode,
        long currentVersion,
        BillingShutdownImpact impact,
        String confirmationToken,
        LocalDateTime confirmationExpiresAt) {
}
