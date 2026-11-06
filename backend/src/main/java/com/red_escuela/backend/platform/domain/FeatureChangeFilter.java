package com.red_escuela.backend.platform.domain;

import com.red_escuela.backend.platform.model.FeatureChangeActorKind;
import com.red_escuela.backend.platform.model.FeatureCode;

import java.time.LocalDate;

public record FeatureChangeFilter(FeatureCode code, Long actorPersonId, FeatureChangeActorKind actorKind, LocalDate from, LocalDate to) {

    public static FeatureChangeFilter byCode(FeatureCode code) {
        return new FeatureChangeFilter(code, null, null, null, null);
    }
}
