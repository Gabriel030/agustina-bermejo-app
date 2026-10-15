package com.red_escuela.backend.shared.event;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {
    private static final List<String> PROPAGATED_KEYS = List.of(CorrelationIds.REQUEST_ID_KEY, CorrelationIds.USER_ID_KEY);

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> captured = new HashMap<>();
        for (String key : PROPAGATED_KEYS) {
            String value = MDC.get(key);
            if (value != null) {
                captured.put(key, value);
            }
        }
        return () -> {
            Map<String, String> previous = MDC.getCopyOfContextMap();
            MDC.setContextMap(captured);
            try {
                task.run();
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        };
    }
}
