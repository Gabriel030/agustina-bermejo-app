package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;
import org.springframework.lang.Nullable;

public record AudienceSampleEntry(Long personId, String fullName, @Nullable String addressMasked, String status,
                                  @Nullable ExclusionReason exclusionReason) {
}
