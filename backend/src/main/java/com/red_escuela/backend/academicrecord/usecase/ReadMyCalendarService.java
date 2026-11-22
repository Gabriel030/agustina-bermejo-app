package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.dto.MyCalendarResponse;
import com.red_escuela.backend.academicrecord.specialist.StudentCalendar;
import com.red_escuela.backend.academicrecord.specialist.StudentCalendarProvider;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@UseCase
public class ReadMyCalendarService implements ReadMyCalendarUseCase {

    private static final int MAX_RANGE_DAYS = 62;

    private final StudentCalendarProvider calendarProvider;
    private final Clock clock;

    public ReadMyCalendarService(StudentCalendarProvider calendarProvider, Clock clock) {
        this.calendarProvider = calendarProvider;
        this.clock = clock;
    }

    @Override
    public MyCalendarResponse read(Long studentId, LocalDate from, LocalDate to) {
        validateRange(from, to);
        StudentCalendar calendar = calendarProvider.calendarOf(studentId, from, to);
        return new MyCalendarResponse(OffsetDateTime.now(clock), calendar.hasActiveEnrollments(), calendar.events());
    }

    @Override
    public MyCalendarResponse readEmpty(LocalDate from, LocalDate to) {
        validateRange(from, to);
        return new MyCalendarResponse(OffsetDateTime.now(clock), false, List.of());
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new RequestFieldRequiredException("from y to son obligatorios");
        }
        if (to.isBefore(from)) {
            throw new RequestFieldOutOfRangeException("from debe ser anterior o igual a to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new RequestFieldOutOfRangeException("El rango no puede superar " + MAX_RANGE_DAYS + " días");
        }
    }
}
