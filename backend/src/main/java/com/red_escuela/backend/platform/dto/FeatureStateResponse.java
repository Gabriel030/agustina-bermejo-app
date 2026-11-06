package com.red_escuela.backend.platform.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import com.red_escuela.backend.platform.domain.FeatureState;
import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

public record FeatureStateResponse(FeatureCode code, boolean enabled, @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) @JsonInclude(JsonInclude.Include.NON_NULL) BillingMode mode, long version) {

    public static FeatureStateResponse of(FeatureCode code, FeatureState state) {
        return new FeatureStateResponse(code, state.enabled(), state.mode(), state.version());
    }
}
