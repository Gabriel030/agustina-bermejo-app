package com.red_escuela.backend.attendance.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettings;
import com.red_escuela.backend.identity.api.InstitutionAcademicSettingsReader;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class JustificationDeadlinePolicyTest {

    private static final LocalDate FRIDAY_CLASS = LocalDate.of(2026, 3, 6);
    private static final LocalDate NEXT_FRIDAY = LocalDate.of(2026, 3, 13);
    private static final LocalDate NEXT_SATURDAY = LocalDate.of(2026, 3, 14);

    public static JustificationDeadlinePolicy policy(LocalDate today, int businessDays, LocalDate... holidays) {
        Set<LocalDate> closed = Set.of(holidays);
        BusinessDayCalendar calendar = day -> day.getDayOfWeek() != DayOfWeek.SATURDAY
                && day.getDayOfWeek() != DayOfWeek.SUNDAY && !closed.contains(day);
        InstitutionAcademicSettingsReader reader = mock(InstitutionAcademicSettingsReader.class);
        when(reader.read()).thenReturn(new InstitutionAcademicSettings(businessDays, 15, false));
        Clock clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        return new JustificationDeadlinePolicy(reader, calendar, clock);
    }

    @Test
    void withinDeadlineOnTheLastBusinessDay() {
        assertThat(policy(NEXT_FRIDAY, 5).isWithinDeadline(FRIDAY_CLASS)).isTrue();
    }

    @Test
    void expiredTheDayAfterTheLastBusinessDay() {
        assertThat(policy(NEXT_SATURDAY, 5).isWithinDeadline(FRIDAY_CLASS)).isFalse();
    }

    @Test
    void usesInstitutionConfiguredDays() {
        LocalDate monday = LocalDate.of(2026, 3, 9);
        assertThat(policy(monday, 1).isWithinDeadline(FRIDAY_CLASS)).isTrue();
        assertThat(policy(monday.plusDays(1), 1).isWithinDeadline(FRIDAY_CLASS)).isFalse();
        assertThat(policy(monday, 0).isWithinDeadline(FRIDAY_CLASS)).isFalse();
        assertThat(policy(FRIDAY_CLASS, 0).isWithinDeadline(FRIDAY_CLASS)).isTrue();
    }

    @Test
    void holidaysDoNotCountAsBusinessDays() {
        LocalDate holiday = LocalDate.of(2026, 3, 9);
        assertThat(policy(LocalDate.of(2026, 3, 16), 5, holiday).isWithinDeadline(FRIDAY_CLASS)).isTrue();
        assertThat(policy(LocalDate.of(2026, 3, 17), 5, holiday).isWithinDeadline(FRIDAY_CLASS)).isFalse();
    }

    @Test
    void deadlinesAreComputedPerDistinctClassDate() {
        LocalDate thursday = LocalDate.of(2026, 3, 5);
        Map<LocalDate, LocalDate> deadlines = policy(NEXT_FRIDAY, 5).deadlinesOf(List.of(FRIDAY_CLASS, thursday, FRIDAY_CLASS));
        assertThat(deadlines).containsOnly(
                Map.entry(FRIDAY_CLASS, NEXT_FRIDAY), Map.entry(thursday, LocalDate.of(2026, 3, 12)));
    }
}
