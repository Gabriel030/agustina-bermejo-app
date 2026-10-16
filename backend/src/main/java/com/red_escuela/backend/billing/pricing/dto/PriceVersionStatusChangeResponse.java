package com.red_escuela.backend.billing.pricing.dto;

import org.springframework.lang.Nullable;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionEvent;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatus;
import com.red_escuela.backend.billing.pricing.domain.PriceVersionStatusChange;

import java.time.Instant;

public record PriceVersionStatusChangeResponse(@Nullable PriceVersionStatus fromStatus, PriceVersionStatus toStatus, PriceVersionEvent event,
                                               @Nullable Long actorPersonId, @Nullable String actorProcess, Instant changedAt, @Nullable String reason) {

    public static PriceVersionStatusChangeResponse from(PriceVersionStatusChange change) {
        return new PriceVersionStatusChangeResponse(change.getFromStatus(), change.getToStatus(), change.getEvent(), change.getActorPersonId(),
                change.getActorProcess(), change.getChangedAt(), change.getReason());
    }
}
