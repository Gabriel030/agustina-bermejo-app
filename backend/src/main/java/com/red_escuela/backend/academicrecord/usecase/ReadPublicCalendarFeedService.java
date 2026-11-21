package com.red_escuela.backend.academicrecord.usecase;

import com.red_escuela.backend.academicrecord.service.CalendarFeedTokenReader.ActiveCalendarFeedToken;
import com.red_escuela.backend.academicrecord.service.PublicCalendarFeedTokenResolver;
import com.red_escuela.backend.academicrecord.specialist.CalendarFeedLocator;
import com.red_escuela.backend.academicrecord.specialist.IcsCalendarWriter;
import com.red_escuela.backend.academicrecord.specialist.MyCalendarEventsFinder;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;

@UseCase
public class ReadPublicCalendarFeedService implements ReadPublicCalendarFeedUseCase {

    private static final int DAYS_BEFORE_TODAY = 14;
    private static final int DAYS_AFTER_TODAY = 120;

    private final PublicCalendarFeedTokenResolver tokenResolver;
    private final MyCalendarEventsFinder eventsFinder;
    private final IcsCalendarWriter icsWriter;
    private final CalendarFeedLocator locator;
    private final Clock clock;

    public ReadPublicCalendarFeedService(PublicCalendarFeedTokenResolver tokenResolver, MyCalendarEventsFinder eventsFinder,
                                         IcsCalendarWriter icsWriter, CalendarFeedLocator locator, Clock clock) {
        this.tokenResolver = tokenResolver;
        this.eventsFinder = eventsFinder;
        this.icsWriter = icsWriter;
        this.locator = locator;
        this.clock = clock;
    }

    @Override
    public byte[] read(String rawToken, String clientAddress) {
        ActiveCalendarFeedToken active = tokenResolver.resolveStudentToken(rawToken, clientAddress);
        LocalDate today = LocalDate.now(clock);
        return icsWriter.write(
                eventsFinder.findEvents(active.studentId(), today.minusDays(DAYS_BEFORE_TODAY), today.plusDays(DAYS_AFTER_TODAY)),
                locator.host(), clock.instant());
    }
}
