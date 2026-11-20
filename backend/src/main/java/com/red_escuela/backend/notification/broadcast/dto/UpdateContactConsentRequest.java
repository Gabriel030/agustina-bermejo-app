package com.red_escuela.backend.notification.broadcast.dto;

import com.red_escuela.backend.notification.broadcast.domain.BroadcastCategory;
import com.red_escuela.backend.notification.broadcast.domain.ConsentChannel;
import com.red_escuela.backend.notification.broadcast.domain.ConsentSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateContactConsentRequest(
        @NotNull ConsentChannel channel,
        @NotNull BroadcastCategory category,
        @NotNull Boolean granted,
        @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED, allowableValues = {"PROFILE"}) ConsentSource source) {
}
