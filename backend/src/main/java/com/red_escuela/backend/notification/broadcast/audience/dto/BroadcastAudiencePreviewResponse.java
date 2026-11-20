package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.ExclusionReason;

import java.util.List;
import java.util.Map;

public record BroadcastAudiencePreviewResponse(int total, int included, Map<ExclusionReason, Integer> excludedByReason,
                                      List<AudienceSampleEntry> sample) {
}
