package com.red_escuela.backend.billing.api;

import com.red_escuela.backend.billing.collections.service.StudentOverdueDebtFinder;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("D-065")
class StudentOverdueDebtQueryTest {

    private static final Long STUDENT_ID = 9L;
    private static final LocalDate TODAY = LocalDate.of(2027, 3, 24);

    private final StudentOverdueDebtFinder finder = mock(StudentOverdueDebtFinder.class);
    private final Clock clock = Clock.fixed(TODAY.atTime(15, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final StudentOverdueDebtQuery query = new StudentOverdueDebtQuery(finder, clock);

    @Test
    void asksTheSingleDefinitionWithTheSystemClockDate() {
        when(finder.hasOverdueDebt(STUDENT_ID, TODAY)).thenReturn(true);
        when(finder.hasOverdueDebt(STUDENT_ID, TODAY.plusDays(1))).thenReturn(false);

        assertThat(query.hasOverdueDebt(STUDENT_ID)).isTrue();
    }

    @Test
    void reportsNoDebtWhenTheSingleDefinitionFindsNone() {
        when(finder.hasOverdueDebt(STUDENT_ID, TODAY)).thenReturn(false);

        assertThat(query.hasOverdueDebt(STUDENT_ID)).isFalse();
    }
}
