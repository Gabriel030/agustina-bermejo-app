package com.red_escuela.backend.billing.run.operation;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceSimulator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@Tag("D-055")
class MonthlyInvoiceSimulationJobTest {

    @Test
    void theCronFiresOnTheTwentyFifthAtFourInTheMorning() {
        CronExpression cron = CronExpression.parse(MonthlyInvoiceSimulationJob.CRON);

        assertThat(cron.next(LocalDateTime.of(2026, 10, 2, 0, 0))).isEqualTo(LocalDateTime.of(2026, 10, 25, 4, 0));
        assertThat(cron.next(LocalDateTime.of(2026, 10, 25, 4, 0))).isEqualTo(LocalDateTime.of(2026, 11, 25, 4, 0));
    }

    @Test
    void theSimulationOfTheTwentyFifthTargetsNextMonthWithTheFirstDayPrice() {
        MonthlyInvoiceSimulator simulator = mock(MonthlyInvoiceSimulator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-12-25T07:00:00Z"), ZoneOffset.UTC);

        new MonthlyInvoiceSimulationJob(simulator, clock).run();

        verify(simulator).simulateMonthlyInvoices(java.time.YearMonth.of(2027, 1), LocalDate.of(2026, 12, 25), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
    }

    @Test
    @Tag("D-511")
    void aScheduledSimulationRunningOnTheTwentiethCountsTheDueDateFromTheFirstDayOfTheSimulatedPeriod() {
        MonthlyInvoiceSimulator simulator = mock(MonthlyInvoiceSimulator.class);
        Clock clock = Clock.fixed(Instant.parse("2026-12-20T07:00:00Z"), ZoneOffset.UTC);

        new MonthlyInvoiceSimulationJob(simulator, clock).run();

        verify(simulator).simulateMonthlyInvoices(java.time.YearMonth.of(2027, 1), LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
    }
}
