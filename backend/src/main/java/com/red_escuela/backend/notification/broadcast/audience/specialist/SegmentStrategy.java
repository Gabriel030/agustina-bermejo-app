package com.red_escuela.backend.notification.broadcast.audience.specialist;

import com.red_escuela.backend.notification.broadcast.audience.domain.SegmentKey;

import java.util.List;
import java.util.Set;

public interface SegmentStrategy {
    SegmentKey key();

    List<String> parameterNames();

    boolean isAvailable();

    Set<Long> resolvePersonIds(String value);
}
