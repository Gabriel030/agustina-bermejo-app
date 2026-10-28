package com.red_escuela.backend.config.demo;

import com.red_escuela.backend.shared.time.ApplicationTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class DemoClock extends Clock {

    private final ZoneId zone;
    private volatile Instant fixedInstant;

    public DemoClock(ZoneId zone) {
        this.zone = zone;
    }

    public void travelTo(LocalDate date) {
        travelTo(date.atTime(12, 0));
    }

    public void travelTo(LocalDateTime dateTime) {
        this.fixedInstant = dateTime.atZone(zone).toInstant();
    }

    public void backToPresent() {
        this.fixedInstant = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        DemoClock copy = new DemoClock(newZone);
        copy.fixedInstant = fixedInstant;
        return copy;
    }

    @Override
    public Instant instant() {
        Instant fixed = fixedInstant;
        return fixed != null ? fixed : Instant.now();
    }

    @Configuration
    @Profile("demo")
    static class DemoClockConfig {

        @Bean
        DemoClock clock(@Value("${app.timezone}") String timezone) {
            DemoClock clock = new DemoClock(ZoneId.of(timezone));
            ApplicationTime.useClock(clock);
            return clock;
        }
    }
}
