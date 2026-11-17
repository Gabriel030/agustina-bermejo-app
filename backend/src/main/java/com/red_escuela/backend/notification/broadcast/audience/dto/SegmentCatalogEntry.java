package com.red_escuela.backend.notification.broadcast.audience.dto;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;

import java.util.List;

public record SegmentCatalogEntry(SegmentKey key, List<String> parameters) {
}
