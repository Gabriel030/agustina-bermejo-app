package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.FeatureChangeEntry;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.LocalDateTime;

public record FeatureChangeResponse(Long id, FeatureCode code, FeatureStateViewResponse before, FeatureStateViewResponse after, ActorSummaryResponse actor,
                                    String actorKind, String reason, LocalDateTime changedAt) {

    public static FeatureChangeResponse of(FeatureChangeEntry entry) {
        return new FeatureChangeResponse(entry.id(), entry.code(), FeatureStateViewResponse.of(entry.before()), FeatureStateViewResponse.of(entry.after()),
                ActorSummaryResponse.of(entry.actor()), entry.actorKind(), entry.reason(), entry.changedAt());
    }
}
