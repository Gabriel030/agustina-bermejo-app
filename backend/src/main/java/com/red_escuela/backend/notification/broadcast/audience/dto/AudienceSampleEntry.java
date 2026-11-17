package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;

public record AudienceSampleEntry(Long personId, String fullName, String addressMasked, String status,
                                  ExclusionReason exclusionReason) {
}
