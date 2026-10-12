package com.red_escuela.backend.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

public final class ApplicationTime {

    private static volatile Clock clock = Clock.systemDefaultZone();

    private ApplicationTime() {
    }

    public static void useClock(Clock newClock) {
        clock = newClock;
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    public static Instant instant() {
        return Instant.now(clock);
    }

    public static LocalDate today() {
        return LocalDate.now(clock);
    }

    public static YearMonth currentMonth() {
        return YearMonth.now(clock);
    }
}
