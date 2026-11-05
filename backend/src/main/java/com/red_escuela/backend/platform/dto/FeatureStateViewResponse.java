package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.FeatureStateView;
import com.red_escuela.backend.shared.module.BillingMode;

public record FeatureStateViewResponse(boolean enabled, BillingMode mode) {

    public static FeatureStateViewResponse of(FeatureStateView view) {
        return view == null ? null : new FeatureStateViewResponse(view.enabled(), view.mode());
    }
}
