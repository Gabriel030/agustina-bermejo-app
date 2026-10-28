package com.red_escuela.backend.shared.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
@Profile("!test & !demo")
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        Clock clock = systemClock(timezone);
        ApplicationTime.useClock(clock);
        return clock;
    }

    public static Clock systemClock(String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
