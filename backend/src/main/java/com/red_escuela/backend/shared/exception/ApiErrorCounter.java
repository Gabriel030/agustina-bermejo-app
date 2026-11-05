package com.red_escuela.backend.shared.exception;

import io.micrometer.core.instrument.MeterRegistry;

public class ApiErrorCounter {

    public static final String METER_NAME = "api.errors";

    private final MeterRegistry meterRegistry;

    public ApiErrorCounter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(int status, String code) {
        meterRegistry.counter(METER_NAME, "status", String.valueOf(status), "code", code).increment();
    }
}
