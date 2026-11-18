package com.red_escuela.backend.notification.broadcast.lifecycle.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import com.red_escuela.backend.notification.broadcast.freezer.domain.BroadcastRecipientStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record BroadcastRecipientResponse(Long personId, String fullName, BroadcastRecipientStatus status,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) ExclusionReason exclusionReason,
                                         @Schema(requiredMode = Schema.RequiredMode.NOT_REQUIRED) String addressMasked) {
}
