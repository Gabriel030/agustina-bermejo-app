package com.red_escuela.backend.attendance.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class AttendanceDecisionRevertWindow {

    public static final int DAYS = 7;

    private AttendanceDecisionRevertWindow() {
    }

    public static LocalDate lastRevertibleDay(LocalDateTime decidedAt) {
        return decidedAt == null ? null : decidedAt.toLocalDate().plusDays(DAYS);
    }

    public static boolean isOpen(LocalDateTime decidedAt, LocalDate today) {
        LocalDate lastDay = lastRevertibleDay(decidedAt);
        return lastDay != null && !today.isAfter(lastDay);
    }
}
