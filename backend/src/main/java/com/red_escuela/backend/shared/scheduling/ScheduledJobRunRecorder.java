package com.red_escuela.backend.shared.scheduling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ScheduledJobRunRecorder {

    public static final String LAST_SUCCESS_METER = "scheduled.job.last.success.epoch.seconds";
    public static final String DURATION_METER = "scheduled.job.duration";

    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    public ScheduledJobRunRecorder(MeterRegistry meterRegistry, Clock clock) {
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public void recordSuccess(String job, Duration elapsed) {
        lastSuccess.computeIfAbsent(job, name -> meterRegistry.gauge(LAST_SUCCESS_METER, Tags.of("job", name), new AtomicLong()))
                .set(clock.instant().getEpochSecond());
        meterRegistry.timer(DURATION_METER, "job", job, "outcome", "success").record(elapsed);
    }

    public void recordFailure(String job, Duration elapsed) {
        meterRegistry.timer(DURATION_METER, "job", job, "outcome", "failure").record(elapsed);
    }
}
