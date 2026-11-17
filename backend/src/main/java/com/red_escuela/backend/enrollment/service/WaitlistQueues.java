package com.red_escuela.backend.enrollment.service;

import com.red_escuela.backend.enrollment.dto.WaitlistKind;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class WaitlistQueues {

    private final Map<WaitlistKind, WaitlistQueue> queuesByKind = new EnumMap<>(WaitlistKind.class);

    public WaitlistQueues(List<WaitlistQueue> queues) {
        queues.forEach(queue -> queuesByKind.put(queue.kind(), queue));
    }

    public WaitlistQueue of(WaitlistKind kind) {
        return queuesByKind.get(kind);
    }

    public List<WaitlistQueue> all() {
        return List.copyOf(queuesByKind.values());
    }
}
