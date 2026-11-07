package com.red_escuela.backend.demo;

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
        return fixed != null ? fixed : Clock.system(zone).instant();
    }
}
