package com.red_escuela.backend.shared.time;

import com.red_escuela.backend.config.ApplicationTimeZone;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class MutableClock extends Clock {

    public static final MutableClock INSTANCE = new MutableClock(ApplicationTimeZone.resolve());

    private final ZoneId zone;
    private volatile Instant fixedInstant;

    private MutableClock(ZoneId zone) {
        this.zone = zone;
    }

    public boolean isFixed() {
        return fixedInstant != null;
    }

    public void setInstant(Instant instant) {
        this.fixedInstant = instant.truncatedTo(ChronoUnit.MICROS);
    }

    public void setDate(LocalDate date) {
        setInstant(date.atStartOfDay(zone).toInstant());
    }

    public void setDateTime(LocalDateTime dateTime) {
        setInstant(dateTime.atZone(zone).toInstant());
    }

    public void advance(Duration duration) {
        Instant current = fixedInstant;
        setInstant((current != null ? current : Instant.now()).plus(duration));
    }

    public void reset() {
        this.fixedInstant = null;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant(), zone);
    }

    @Override
    public Instant instant() {
        Instant current = fixedInstant;
        return current != null ? current : Instant.now();
    }
}
