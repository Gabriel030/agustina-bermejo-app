package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import jakarta.validation.constraints.NotNull;

public record UpdateContactConsentRequest(
        @NotNull ConsentChannel channel,
        @NotNull BroadcastCategory category,
        @NotNull Boolean granted) {
}
