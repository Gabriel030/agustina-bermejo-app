package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.module.BillingMode;
import com.red_escuela.backend.platform.model.FeatureCode;

public record FeatureTransitionRequest(FeatureCode code, BillingMode target) {
}
