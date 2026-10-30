package com.red_escuela.backend.billing.run.usecase;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationAlertResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationLineResponse;
import com.red_escuela.backend.billing.run.dto.MonthlyInvoiceSimulationResponse;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationAlertRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationLineRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceSimulator;
import com.red_escuela.backend.shared.UseCase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@UseCase
public class MonthlyInvoiceSimulationService implements MonthlyInvoiceSimulationUseCase {
    private final MonthlyInvoiceSimulator simulator;
    private final MonthlyInvoiceSimulationLineRepository lineRepository;
    private final MonthlyInvoiceSimulationAlertRepository alertRepository;
    private final Clock clock;

    public MonthlyInvoiceSimulationService(MonthlyInvoiceSimulator simulator, MonthlyInvoiceSimulationLineRepository lineRepository,
                                           MonthlyInvoiceSimulationAlertRepository alertRepository, Clock clock) {
        this.simulator = simulator;
        this.lineRepository = lineRepository;
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    @Override
    public MonthlyInvoiceSimulationResponse simulateMonthlyInvoicesOfPeriodAsOfToday(YearMonth period) {
        LocalDate today = LocalDate.now(clock);
        MonthlyInvoiceRun run = simulator.simulateMonthlyInvoices(period, today, period.atDay(1), MonthlyInvoiceRunTrigger.MANUAL);
        return MonthlyInvoiceSimulationResponse.of(run,
                lineRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().map(MonthlyInvoiceSimulationLineResponse::of).toList(),
                alertRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream().map(MonthlyInvoiceSimulationAlertResponse::of).toList());
    }
}
