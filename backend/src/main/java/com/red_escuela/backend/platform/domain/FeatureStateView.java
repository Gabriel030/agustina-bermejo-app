package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.shared.module.BillingMode;

public record FeatureStateView(boolean enabled, BillingMode mode) {
}
