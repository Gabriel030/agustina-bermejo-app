package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.LocalDateTime;

public record FeatureChangeEntry(Long id, FeatureCode code, FeatureStateView before, FeatureStateView after, ActorSummary actor, String actorKind, String reason, LocalDateTime changedAt) {
}
