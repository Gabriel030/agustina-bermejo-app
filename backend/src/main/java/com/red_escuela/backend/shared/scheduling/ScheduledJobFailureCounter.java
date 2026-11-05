package com.red_escuela.backend.shared.scheduling;

import io.micrometer.core.instrument.MeterRegistry;

public class ScheduledJobFailureCounter {

    public static final String METER_NAME = "scheduled.job.failures";

    private final MeterRegistry meterRegistry;

    public ScheduledJobFailureCounter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(String job, Throwable failure) {
        meterRegistry.counter(METER_NAME, "job", job, "cause", failure.getClass().getSimpleName()).increment();
    }
}
