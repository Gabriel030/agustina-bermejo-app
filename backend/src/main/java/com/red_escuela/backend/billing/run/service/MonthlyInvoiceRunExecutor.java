package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItem;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.policy.NightlyBillingCompletedTodayContext;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.billing.service.BillingConfigurationAlertService;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@Service
public class MonthlyInvoiceRunExecutor {
    private static final Logger log = LoggerFactory.getLogger(MonthlyInvoiceRunExecutor.class);

    private final MonthlyInvoiceRunRepository runRepository;
    private final NightlyBillingRunRepository nightlyRunRepository;
    private final MonthlyInvoiceCandidateFinder candidateFinder;
    private final MonthlyInvoiceRunItemProcessor itemProcessor;
    private final BillingConfigurationAlertService alertService;
    private final PolicySet<NightlyBillingCompletedTodayContext> nightlyCompletedPolicySet;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public MonthlyInvoiceRunExecutor(MonthlyInvoiceRunRepository runRepository, NightlyBillingRunRepository nightlyRunRepository,
                                     MonthlyInvoiceCandidateFinder candidateFinder, MonthlyInvoiceRunItemProcessor itemProcessor,
                                     BillingConfigurationAlertService alertService,
                                     PolicySet<NightlyBillingCompletedTodayContext> nightlyBillingCompletedTodayPolicySet, MeterRegistry meterRegistry, Clock clock) {
        this.runRepository = runRepository;
        this.nightlyRunRepository = nightlyRunRepository;
        this.candidateFinder = candidateFinder;
        this.itemProcessor = itemProcessor;
        this.alertService = alertService;
        this.nightlyCompletedPolicySet = nightlyBillingCompletedTodayPolicySet;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public Optional<MonthlyInvoiceRun> runMonthlyInvoicesOfBusinessDate(LocalDate businessDate, MonthlyInvoiceRunTrigger trigger) {
        return runMonthlyInvoicesOfPeriod(YearMonth.from(businessDate), businessDate, trigger);
    }

    public Optional<MonthlyInvoiceRun> runMonthlyInvoicesOfPeriod(YearMonth period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger) {
        Optional<PolicyVerdict> nightDenial = nightlyCompletedPolicySet.evaluateAllRules(nightlyContextOf(businessDate)).firstDenial();
        return nightDenial.isPresent()
                ? Optional.of(recordRunBlockedByNight(period, businessDate, trigger, nightDenial.get().code()))
                : startRunUnlessPeriodAlreadyHasOne(period, businessDate, trigger);
    }

    private NightlyBillingCompletedTodayContext nightlyContextOf(LocalDate businessDate) {
        return nightlyRunRepository.findByBusinessDate(businessDate)
                .map(night -> new NightlyBillingCompletedTodayContext(businessDate, night.getBusinessDate(), night.getStatus()))
                .orElseGet(() -> NightlyBillingCompletedTodayContext.withoutAnyRun(businessDate));
    }

    private MonthlyInvoiceRun recordRunBlockedByNight(YearMonth period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger, String failureCode) {
        log.warn("Corrida mensual no iniciada: periodo={}, motivo={}", period, failureCode);
        return runRepository.save(MonthlyInvoiceRun.recordRunBlockedByNight(period.atDay(1), businessDate, trigger, failureCode, LocalDateTime.now(clock)));
    }

    private Optional<MonthlyInvoiceRun> startRunUnlessPeriodAlreadyHasOne(YearMonth period, LocalDate businessDate, MonthlyInvoiceRunTrigger trigger) {
        MonthlyInvoiceRun run;
        try {
            run = runRepository.saveAndFlush(MonthlyInvoiceRun.startRealDirectRun(period.atDay(1), businessDate, trigger, LocalDateTime.now(clock)));
        } catch (DataIntegrityViolationException e) {
            log.info("Corrida mensual ignorada: el periodo {} ya tiene una corrida real", period);
            return Optional.empty();
        }
        return Optional.of(sweepAndFinish(run, period, businessDate));
    }

    private MonthlyInvoiceRun sweepAndFinish(MonthlyInvoiceRun run, YearMonth period, LocalDate pricingDate) {
        log.info("Corrida mensual iniciada: periodo={}, corrida={}", period, run.getId());
        Map<MonthlyInvoiceRunItemResult, Integer> counters = new EnumMap<>(MonthlyInvoiceRunItemResult.class);
        try {
            sweepProgramEnrollments(run, period, pricingDate, counters);
            sweepExpiredAnnualProgramEnrollments(run, period, pricingDate, counters);
            sweepCourseEnrollments(run, period, pricingDate, counters);
        } catch (RuntimeException e) {
            log.error("Corrida mensual abortada: periodo={}", period, e);
            run.abortMonthlyRun("SWEEP_ABORTED", LocalDateTime.now(clock));
            return runRepository.save(run);
        }
        int failed = counters.getOrDefault(MonthlyInvoiceRunItemResult.FAILED, 0);
        run.finishMonthlyRun(failed == 0 ? MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN : MonthlyInvoiceRunEvent.RUN_FINISHED_WITH_FAILURES, LocalDateTime.now(clock));
        MonthlyInvoiceRun finished = runRepository.save(run);
        log.info("Corrida mensual finalizada: periodo={}, estado={}, emitidas={}, noCorresponde={}, yaEmitidas={}, fallidas={}", period, finished.getStatus(),
                counters.getOrDefault(MonthlyInvoiceRunItemResult.ISSUED, 0), counters.getOrDefault(MonthlyInvoiceRunItemResult.NOT_APPLICABLE, 0),
                counters.getOrDefault(MonthlyInvoiceRunItemResult.ALREADY_ISSUED, 0), failed);
        refreshConfigurationAlerts(period);
        return finished;
    }

    private void refreshConfigurationAlerts(YearMonth period) {
        try {
            alertService.refresh(period);
        } catch (RuntimeException e) {
            log.error("Falló la actualización de alertas de configuración de cobro: periodo={}", period, e);
        }
    }

    private void sweepProgramEnrollments(MonthlyInvoiceRun run, YearMonth period, LocalDate pricingDate, Map<MonthlyInvoiceRunItemResult, Integer> counters) {
        sweep(run, MonthlyInvoiceEnrollmentType.PROGRAM, period, pricingDate, counters, lastId -> candidateFinder.findProgramEnrollmentIdsAfter(period, lastId, false));
    }

    private void sweepExpiredAnnualProgramEnrollments(MonthlyInvoiceRun run, YearMonth period, LocalDate pricingDate, Map<MonthlyInvoiceRunItemResult, Integer> counters) {
        sweep(run, MonthlyInvoiceEnrollmentType.PROGRAM, period, pricingDate, counters,
                lastId -> candidateFinder.findProgramEnrollmentIdsAfter(period, lastId, true));
    }

    private void sweepCourseEnrollments(MonthlyInvoiceRun run, YearMonth period, LocalDate pricingDate, Map<MonthlyInvoiceRunItemResult, Integer> counters) {
        sweep(run, MonthlyInvoiceEnrollmentType.COURSE, period, pricingDate, counters, lastId -> candidateFinder.findCourseEnrollmentIdsAfter(period, lastId));
    }

    private void sweep(MonthlyInvoiceRun run, MonthlyInvoiceEnrollmentType type, YearMonth period, LocalDate pricingDate,
                       Map<MonthlyInvoiceRunItemResult, Integer> counters, Function<Long, List<Long>> nextPage) {
        Long lastId = 0L;
        List<Long> page = nextPage.apply(lastId);
        while (!page.isEmpty()) {
            for (Long enrollmentId : page) {
                MonthlyInvoiceRunItem item = itemProcessor.issueAndRecordItem(run.getId(), type, enrollmentId, period, pricingDate);
                counters.merge(item.getResult(), 1, Integer::sum);
                meterRegistry.counter("monthly_invoice_run_items_total", "result", item.getResult().name()).increment();
            }
            lastId = page.get(page.size() - 1);
            page = nextPage.apply(lastId);
        }
    }
}
