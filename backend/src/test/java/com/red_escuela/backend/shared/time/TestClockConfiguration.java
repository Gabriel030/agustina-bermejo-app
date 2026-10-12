package com.red_escuela.backend.shared.time;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration
@Profile("test")
public class TestClockConfiguration {

    @Bean
    public Clock clock() {
        ApplicationTime.useClock(MutableClock.INSTANCE);
        return MutableClock.INSTANCE;
    }
}
