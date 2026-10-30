package com.red_escuela.backend.billing.service;

import com.red_escuela.backend.billing.entity.CohortIntake;
import com.red_escuela.backend.billing.entity.PaymentPlanCadence;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlert;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlertType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationLine;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunItemRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationAlertRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationLineRepository;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceRunExecutor;
import com.red_escuela.backend.billing.run.service.MonthlyInvoiceSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("D-055")
@Tag("D-453")
class MonthlyInvoiceSimulationTest extends AbstractInvoiceSequenceTest {
    private static final YearMonth APRIL = YearMonth.of(2026, 4);

    @Autowired
    private MonthlyInvoiceSimulator simulator;
    @Autowired
    private com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceRunQueryUseCase queryUseCase;
    @Autowired
    private com.red_escuela.backend.billing.run.usecase.MonthlyInvoiceSimulationUseCase simulationUseCase;
    @Autowired
    private MonthlyInvoiceRunExecutor executor;
    @Autowired
    private MonthlyInvoiceRunRepository runRepository;
    @Autowired
    private MonthlyInvoiceRunItemRepository itemRepository;
    @Autowired
    private MonthlyInvoiceSimulationLineRepository lineRepository;
    @Autowired
    private MonthlyInvoiceSimulationAlertRepository alertRepository;

    @BeforeEach
    void forgetPreviousRuns() {
        jdbcTemplate.update("delete from monthly_invoice_simulation_alert");
        jdbcTemplate.update("delete from monthly_invoice_simulation_line");
        jdbcTemplate.update("delete from monthly_invoice_run_item_attempt");
        jdbcTemplate.update("delete from monthly_invoice_run_item");
        jdbcTemplate.update("delete from monthly_invoice_run");
    }

    private Long enrollmentOfAprilCohort() {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 3, 25));
        return newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, APRIL, date(2026, 3, 10));
    }

    private MonthlyInvoiceSimulationLine lineOf(MonthlyInvoiceRun run, Long enrollmentId) {
        return lineRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(run.getId()).stream()
                .filter(line -> line.getEnrollmentType() == MonthlyInvoiceEnrollmentType.PROGRAM && line.getEnrollmentId().equals(enrollmentId)).findFirst().orElseThrow();
    }

    private Map<String, String> fingerprintOfEveryTableOutsideTheRunRegistry() {
        Map<String, String> fingerprints = new TreeMap<>();
        List<String> tables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = current_schema() and table_type = 'BASE TABLE'
                  and table_name not like 'monthly_invoice_%' and table_name not like 'flyway%' and table_name not like 'shedlock%'
                """, String.class);
        for (String table : tables) {
            fingerprints.put(table, jdbcTemplate.queryForObject(
                    "select count(*) || ':' || coalesce(md5(string_agg(t::text, '|' order by t::text)), '') from \"" + table + "\" t", String.class));
        }
        return fingerprints;
    }

    @Test
    void simulationLeavesTheDatabaseIdenticalAndAnnouncesWhatTheRealRunThenIssues() {
        Long enrollment = enrollmentOfAprilCohort();
        Map<String, String> before = fingerprintOfEveryTableOutsideTheRunRegistry();
        long invoicesBefore = jdbcTemplate.queryForObject("select count(*) from invoice", Long.class);

        MonthlyInvoiceRun simulation = simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThat(fingerprintOfEveryTableOutsideTheRunRegistry()).isEqualTo(before);
        assertThat(jdbcTemplate.queryForObject("select count(*) from invoice", Long.class)).isEqualTo(invoicesBefore);
        assertThat(simulation.getMode()).isEqualTo(MonthlyInvoiceRunMode.SIMULATION);
        assertThat(simulation.getStatus()).isEqualTo(MonthlyInvoiceRunStatus.COMPLETED);
        MonthlyInvoiceSimulationLine simulated = lineOf(simulation, enrollment);
        assertThat(simulated.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.ISSUED);

        today(date(2026, 4, 1));
        jdbcTemplate.update("insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at) values (?, 'COMPLETED', 'SCHEDULED', ?, ?) on conflict (business_date) do nothing",
                date(2026, 4, 1), LocalDateTime.of(2026, 4, 1, 3, 0), LocalDateTime.of(2026, 4, 1, 3, 30));
        MonthlyInvoiceRun real = executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();

        assertThat(real.getMode()).isEqualTo(MonthlyInvoiceRunMode.REAL);
        MonthlyInvoiceRunItem item = itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(real.getId()).stream()
                .filter(candidate -> candidate.getEnrollmentId().equals(enrollment)).findFirst().orElseThrow();
        assertThat(item.getResult()).isEqualTo(simulated.getResult());
        assertThat(item.getInvoiceIds()).hasSize(simulated.getChargeCount());
        String idList = item.getInvoiceIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        java.math.BigDecimal issuedTotal = jdbcTemplate.queryForObject("select sum(total) from invoice where id in (" + idList + ")", java.math.BigDecimal.class);
        assertThat(issuedTotal).isEqualByComparingTo(simulated.getTotal().value().amount());
    }

    @Test
    void anotherSimulationOfTheSameDayReplacesTheFirstAndNeverBlocksTheRealRunOfThePeriod() {
        enrollmentOfAprilCohort();

        MonthlyInvoiceRun first = simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);
        MonthlyInvoiceRun second = simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.MANUAL);

        List<MonthlyInvoiceRun> runs = runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1));
        assertThat(runs).extracting(MonthlyInvoiceRun::getId).containsExactly(second.getId());
        assertThat(runs.get(0).getTrigger()).isEqualTo(MonthlyInvoiceRunTrigger.MANUAL);
        assertThat(lineRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(first.getId())).isEmpty();
        today(date(2026, 4, 1));
        jdbcTemplate.update("insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at) values (?, 'COMPLETED', 'SCHEDULED', ?, ?) on conflict (business_date) do nothing",
                date(2026, 4, 1), LocalDateTime.of(2026, 4, 1, 3, 0), LocalDateTime.of(2026, 4, 1, 3, 30));
        assertThat(executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED)).isPresent();
    }

    @Test
    void aYearWithoutTermConfiguredIsShownAsNotIssuedOutOfTheTotalAndRaisesAnAlert() {
        Long enrollment = enrollmentOfAprilCohort();

        MonthlyInvoiceRun simulation = simulator.simulateMonthlyInvoices(YearMonth.of(2027, 4), date(2026, 3, 25), date(2027, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        MonthlyInvoiceSimulationLine line = lineOf(simulation, enrollment);
        assertThat(line.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.NOT_APPLICABLE);
        assertThat(line.getDecisionCode()).isEqualTo("YEAR_NOT_CONFIGURED");
        assertThat(line.getTotal()).isNull();
        List<MonthlyInvoiceSimulationAlert> alerts = alertRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(simulation.getId());
        assertThat(alerts).extracting(MonthlyInvoiceSimulationAlert::getAlertType).contains(MonthlyInvoiceSimulationAlertType.YEAR_NOT_CONFIGURED);
        assertThat(alerts.get(0).getOfferingId()).isNotNull();
    }

    @Test
    void aMissingPriceIsShownAsAFailureWithTheCareerAndRaisesAnAlert() {
        Long enrollment = enrollmentOfAprilCohort();

        MonthlyInvoiceRun simulation = simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2019, 6, 1), MonthlyInvoiceRunTrigger.MANUAL);

        MonthlyInvoiceSimulationLine line = lineOf(simulation, enrollment);
        assertThat(line.getResult()).isEqualTo(MonthlyInvoiceRunItemResult.FAILED);
        assertThat(line.getOfferingId()).isNotNull();
        assertThat(alertRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(simulation.getId()))
                .anySatisfy(alert -> {
                    assertThat(alert.getOfferingId()).isEqualTo(line.getOfferingId());
                    assertThat(alert.getAlertType()).isIn(MonthlyInvoiceSimulationAlertType.PRICE_MISSING, MonthlyInvoiceSimulationAlertType.EMISSION_ERROR);
                });
    }

    @Test
    @Tag("D-161")
    void aManualSimulationAfterAMidMonthPriceIncreaseUsesTheFirstDayPriceOfThePeriodLikeTheRealRun() {
        Career career = newCareer();
        Year year = newYear(career, 2026, 10, 1, 2, null);
        Long cohort = newCohort(year, CohortIntake.FIRST, date(2026, 3, 1));
        today(date(2026, 3, 25));
        Long enrollment = newEnrollment(career, cohort, PaymentPlanCadence.MONTHLY, APRIL, date(2026, 3, 10));
        newPrice(career, com.red_escuela.backend.billing.invoice.domain.FeeType.MONTHLY_INSTALLMENT, 99999, date(2026, 4, 2));
        today(date(2026, 4, 3));

        var response = simulationUseCase.simulateMonthlyInvoicesOfPeriodAsOfToday(APRIL);

        assertThat(response.pricingDate()).isEqualTo(date(2026, 4, 1));
        var simulated = response.lines().stream().filter(line -> line.enrollmentId().equals(enrollment)).findFirst().orElseThrow();
        jdbcTemplate.update("insert into nightly_billing_run (business_date, status, trigger, started_at, finished_at) values (?, 'COMPLETED', 'SCHEDULED', ?, ?) on conflict (business_date) do nothing",
                date(2026, 4, 1), LocalDateTime.of(2026, 4, 1, 3, 0), LocalDateTime.of(2026, 4, 1, 3, 30));
        MonthlyInvoiceRun real = executor.runMonthlyInvoicesOfPeriod(APRIL, date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED).orElseThrow();
        MonthlyInvoiceRunItem item = itemRepository.findByMonthlyInvoiceRunIdOrderByIdAsc(real.getId()).stream()
                .filter(candidate -> candidate.getEnrollmentId().equals(enrollment)).findFirst().orElseThrow();
        String idList = item.getInvoiceIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        assertThat(jdbcTemplate.queryForObject("select sum(total) from invoice where id in (" + idList + ")", java.math.BigDecimal.class))
                .isEqualByComparingTo(simulated.total().value().amount());
    }

    @Test
    void twoConcurrentSimulationsOfTheSameDayLeaveExactlyOneAndNeitherFails() throws Exception {
        enrollmentOfAprilCohort();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED));
            var second = pool.submit(() -> simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.MANUAL));
            first.get();
            second.get();
        } finally {
            pool.shutdown();
        }
        assertThat(runRepository.findByPeriodOrderByIdAsc(APRIL.atDay(1))).hasSize(1);
    }

    @Test
    void aSimulationIsInvisibleToTheRealRunQueries() {
        enrollmentOfAprilCohort();
        MonthlyInvoiceRun simulation = simulator.simulateMonthlyInvoices(APRIL, date(2026, 3, 25), date(2026, 4, 1), MonthlyInvoiceRunTrigger.SCHEDULED);

        assertThatThrownBy(() -> queryUseCase.findMonthlyInvoiceRun(simulation.getId()))
                .isInstanceOf(com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException.class);
        assertThatThrownBy(() -> queryUseCase.listMonthlyInvoiceRunItems(simulation.getId(), null, null, null))
                .isInstanceOf(com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException.class);
        assertThat(queryUseCase.listMonthlyInvoiceRuns(APRIL, null, null).items()).isEmpty();
        assertThat(queryUseCase.listMonthlyInvoiceRuns(null, 0, 200).items()).noneMatch(run -> run.id().equals(simulation.getId()));
    }
}
