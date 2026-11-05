package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationAlertRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationLineRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceSimulator;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("D-511")
class MonthlyInvoiceSimulationServiceTest {

    @Test
    void theSimulationBeforeAManualIssuanceUsesTodayAsTheIssueDateSoItShowsTheDueDateTheManualIssuanceGives() {
        MonthlyInvoiceSimulator simulator = mock(MonthlyInvoiceSimulator.class);
        YearMonth april = YearMonth.of(2026, 4);
        MonthlyInvoiceRun run = MonthlyInvoiceRun.startSimulationRun(april.atDay(1), LocalDate.of(2026, 4, 10), april.atDay(1), MonthlyInvoiceRunTrigger.MANUAL, LocalDateTime.of(2026, 4, 10, 8, 0));
        when(simulator.simulateMonthlyInvoices(any(), any(), any(), any(), any())).thenReturn(run);
        Clock clock = Clock.fixed(Instant.parse("2026-04-10T12:00:00Z"), ZoneOffset.UTC);
        MonthlyInvoiceSimulationService service = new MonthlyInvoiceSimulationService(simulator, mock(MonthlyInvoiceSimulationLineRepository.class), mock(MonthlyInvoiceSimulationAlertRepository.class), clock);

        service.simulateMonthlyInvoicesOfPeriodAsOfToday(april);

        verify(simulator).simulateMonthlyInvoices(april, LocalDate.of(2026, 4, 10), april.atDay(1), LocalDate.of(2026, 4, 10), MonthlyInvoiceRunTrigger.MANUAL);
    }
}
