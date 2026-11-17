package com.red_escuela.backend.notification.broadcast.audience.domain;

import java.util.List;

public record SegmentDescriptor(SegmentKey key, List<String> parameters) {
}
