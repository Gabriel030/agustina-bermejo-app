package com.red_escuela.backend.platform.dto;

import com.red_escuela.backend.platform.domain.ActorSummary;

public record ActorSummaryResponse(Long id, String fullName) {

    public static ActorSummaryResponse of(ActorSummary actor) {
        return actor == null ? null : new ActorSummaryResponse(actor.id(), actor.fullName());
    }
}
