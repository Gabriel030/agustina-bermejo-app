package com.red_escuela.backend.notification.broadcast.freezer.domain;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;

public record ExclusionReasonCount(ExclusionReason reason, long total) {
}
