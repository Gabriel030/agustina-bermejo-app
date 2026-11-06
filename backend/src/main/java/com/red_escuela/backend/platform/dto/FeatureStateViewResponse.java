package com.red_escuela.backend.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.platform.domain.FeatureStateView;
import com.red_escuela.backend.shared.module.BillingMode;

public record FeatureStateViewResponse(boolean enabled, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL) BillingMode mode) {

    public static FeatureStateViewResponse of(FeatureStateView view) {
        return view == null ? null : new FeatureStateViewResponse(view.enabled(), view.mode());
    }
}
