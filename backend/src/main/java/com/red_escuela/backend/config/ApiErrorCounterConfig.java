package com.red_escuela.backend.config;

import com.red_escuela.backend.shared.exception.ApiErrorCounter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiErrorCounterConfig {

    @Bean
    public ApiErrorCounter apiErrorCounter(MeterRegistry meterRegistry) {
        return new ApiErrorCounter(meterRegistry);
    }
}
