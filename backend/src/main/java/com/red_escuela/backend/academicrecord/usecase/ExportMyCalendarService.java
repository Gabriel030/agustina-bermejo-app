package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.specialist.CalendarFeedLocator;
import com.red_escuela.backend.academicrecord.specialist.IcsCalendarWriter;
import com.red_escuela.backend.academicrecord.specialist.MyCalendarEventsFinder;
import com.red_escuela.backend.shared.UseCase;
import com.red_escuela.backend.shared.exception.RequestFieldOutOfRangeException;
import com.red_escuela.backend.shared.exception.RequestFieldRequiredException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@UseCase
public class ExportMyCalendarService implements ExportMyCalendarUseCase {

    private static final int MAX_RANGE_DAYS = 62;

    private final MyCalendarEventsFinder eventsFinder;
    private final IcsCalendarWriter icsWriter;
    private final CalendarFeedLocator locator;
    private final Clock clock;

    public ExportMyCalendarService(MyCalendarEventsFinder eventsFinder, IcsCalendarWriter icsWriter,
                                   CalendarFeedLocator locator, Clock clock) {
        this.eventsFinder = eventsFinder;
        this.icsWriter = icsWriter;
        this.locator = locator;
        this.clock = clock;
    }

    @Override
    public byte[] export(ExportMyCalendarCommand command) {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = command.from();
        LocalDate to = command.to();
        if (from == null && to == null) {
            from = today;
            to = today.plusDays(MAX_RANGE_DAYS - 1L);
        }
        if (from == null || to == null) {
            throw new RequestFieldRequiredException("from y to son obligatorios juntos");
        }
        if (to.isBefore(from)) {
            throw new RequestFieldOutOfRangeException("from debe ser anterior o igual a to");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new RequestFieldOutOfRangeException("El rango no puede superar " + MAX_RANGE_DAYS + " días");
        }
        return icsWriter.write(eventsFinder.findEvents(command.studentId(), from, to), locator.host(), clock.instant());
    }
}
