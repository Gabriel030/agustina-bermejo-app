package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.run.domain.AtypicalInstallmentIncreaseRule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceEnrollmentType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunItemResult;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunTrigger;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlert;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationAlertType;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationLine;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceSimulationOutcome;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationAlertRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceSimulationLineRepository;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor.MonthlyIssuancePreview;
import com.red_escuela.backend.billing.service.MonthlyInstallmentRowProcessor.PreviewedCharge;
import com.red_escuela.backend.shared.domain.Currency;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class MonthlyInvoiceSimulator {
    private static final Logger log = LoggerFactory.getLogger(MonthlyInvoiceSimulator.class);

    private final MonthlyInvoiceRunRepository runRepository;
    private final MonthlyInvoiceSimulationLineRepository lineRepository;
    private final MonthlyInvoiceSimulationAlertRepository alertRepository;
    private final MonthlyInvoiceCandidateFinder candidateFinder;
    private final MonthlyInstallmentRowProcessor rowProcessor;
    private final TransactionTemplate transaction;
    private final Clock clock;

    public MonthlyInvoiceSimulator(MonthlyInvoiceRunRepository runRepository, MonthlyInvoiceSimulationLineRepository lineRepository,
                                   MonthlyInvoiceSimulationAlertRepository alertRepository, MonthlyInvoiceCandidateFinder candidateFinder,
                                   MonthlyInstallmentRowProcessor rowProcessor, PlatformTransactionManager transactionManager, Clock clock) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.runRepository = runRepository;
        this.lineRepository = lineRepository;
        this.alertRepository = alertRepository;
        this.candidateFinder = candidateFinder;
        this.rowProcessor = rowProcessor;
        this.clock = clock;
    }

    public MonthlyInvoiceRun simulateMonthlyInvoices(YearMonth period, LocalDate businessDate, LocalDate pricingDate, MonthlyInvoiceRunTrigger trigger) {
        return transaction.execute(status -> replaceSimulation(period, businessDate, pricingDate, trigger));
    }

    private MonthlyInvoiceRun replaceSimulation(YearMonth period, LocalDate businessDate, LocalDate pricingDate, MonthlyInvoiceRunTrigger trigger) {
        lineRepository.lockSimulationsOfPeriodAndBusinessDate(period.atDay(1).toEpochDay() * 100000L + businessDate.toEpochDay());
        runRepository.deleteSimulationsOfPeriodAndBusinessDate(period.atDay(1), businessDate);
        MonthlyInvoiceRun run = runRepository.saveAndFlush(MonthlyInvoiceRun.startSimulationRun(period.atDay(1), businessDate, pricingDate, trigger, LocalDateTime.now(clock)));
        log.info("Simulacion mensual iniciada: periodo={}, corrida={}, fechaDePrecio={}", period, run.getId(), pricingDate);
        SimulationTally tally = new SimulationTally();
        candidateFinder.forEachMonthlyCandidate(period, (type, enrollmentId) -> simulateEnrollment(run.getId(), type, enrollmentId, period, pricingDate, tally));
        alertRepository.saveAll(tally.alertsOf(run.getId(), previousInstallmentTotalsOf(period)));
        run.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_FINISHED_CLEAN, LocalDateTime.now(clock));
        log.info("Simulacion mensual finalizada: periodo={}, corrida={}, emitiria={}, noCorresponde={}, yaEmitidas={}, fallarian={}", period, run.getId(),
                tally.count(MonthlyInvoiceRunItemResult.ISSUED), tally.count(MonthlyInvoiceRunItemResult.NOT_APPLICABLE),
                tally.count(MonthlyInvoiceRunItemResult.ALREADY_ISSUED), tally.count(MonthlyInvoiceRunItemResult.FAILED));
        return runRepository.save(run);
    }

    private void simulateEnrollment(Long runId, MonthlyInvoiceEnrollmentType type, Long enrollmentId, YearMonth period, LocalDate pricingDate, SimulationTally tally) {
        MonthlyInvoiceSimulationOutcome outcome = simulateOutcomeOf(type, enrollmentId, period, pricingDate);
        lineRepository.save(MonthlyInvoiceSimulationLine.recordSimulatedOutcome(runId, type, enrollmentId, outcome));
        tally.record(type, outcome);
    }

    private MonthlyInvoiceSimulationOutcome simulateOutcomeOf(MonthlyInvoiceEnrollmentType type, Long enrollmentId, YearMonth period, LocalDate pricingDate) {
        try {
            MonthlyIssuancePreview preview = switch (type) {
                case PROGRAM -> rowProcessor.previewProgramEnrollmentIssuance(enrollmentId, period, pricingDate);
                case COURSE -> rowProcessor.previewCourseEnrollmentIssuance(enrollmentId, period, pricingDate);
            };
            return outcomeOf(preview).withOfferingId(offeringIdOf(type, enrollmentId));
        } catch (RuntimeException e) {
            log.warn("La simulacion de la inscripcion falla: tipo={}, id={}, causa={}", type, enrollmentId, e.getClass().getSimpleName());
            return MonthlyInvoiceSimulationOutcome.wouldFail(offeringIdOf(type, enrollmentId), e.getClass().getSimpleName(), e.getMessage());
        }
    }

    private Long offeringIdOf(MonthlyInvoiceEnrollmentType type, Long enrollmentId) {
        return switch (type) {
            case PROGRAM -> lineRepository.findProgramIdOfProgramEnrollment(enrollmentId);
            case COURSE -> lineRepository.findCourseIdOfCourseEnrollment(enrollmentId);
        };
    }

    static MonthlyInvoiceSimulationOutcome outcomeOf(MonthlyIssuancePreview preview) {
        if (!preview.wouldBeIssued().isEmpty()) {
            return MonthlyInvoiceSimulationOutcome.wouldBeIssued(null, preview.wouldBeIssued().size(), sumOf(preview.wouldBeIssued(), null),
                    sumOf(preview.wouldBeIssued(), FeeType.MONTHLY_INSTALLMENT));
        }
        return preview.alreadyIssuedInvoiceIds().isEmpty()
                ? MonthlyInvoiceSimulationOutcome.wouldNotBeIssued(null, preview.planningDecisionCodes())
                : MonthlyInvoiceSimulationOutcome.wouldBeAlreadyIssued(null);
    }

    private static NonNegativeMoney sumOf(List<PreviewedCharge> charges, FeeType onlyKind) {
        return charges.stream().filter(charge -> onlyKind == null || charge.kind() == onlyKind).map(PreviewedCharge::total).reduce(NonNegativeMoney::plus).orElse(null);
    }

    private Map<OfferingKey, Money> previousInstallmentTotalsOf(YearMonth period) {
        Map<OfferingKey, Money> totals = new LinkedHashMap<>();
        for (Object[] row : lineRepository.sumIssuedInstallmentsByOffering(period.minusMonths(1).atDay(1))) {
            totals.put(new OfferingKey(MonthlyInvoiceEnrollmentType.valueOf((String) row[0]), ((Number) row[1]).longValue()),
                    Money.of((BigDecimal) row[3], Currency.valueOf((String) row[2])));
        }
        return totals;
    }

    record OfferingKey(MonthlyInvoiceEnrollmentType type, Long offeringId) {
    }

    static final class SimulationTally {
        private final Map<MonthlyInvoiceRunItemResult, Integer> counters = new EnumMap<>(MonthlyInvoiceRunItemResult.class);
        private final Map<OfferingKey, Map<MonthlyInvoiceSimulationAlertType, Integer>> alertCounts = new LinkedHashMap<>();
        private final Map<OfferingKey, Money> installmentTotals = new LinkedHashMap<>();

        int count(MonthlyInvoiceRunItemResult result) {
            return counters.getOrDefault(result, 0);
        }

        void record(MonthlyInvoiceEnrollmentType type, MonthlyInvoiceSimulationOutcome outcome) {
            counters.merge(outcome.result(), 1, Integer::sum);
            if (outcome.offeringId() == null) {
                return;
            }
            OfferingKey key = new OfferingKey(type, outcome.offeringId());
            if (outcome.installmentTotal() != null) {
                installmentTotals.merge(key, outcome.installmentTotal().value(), Money::plus);
            }
            outcome.findAlertType().ifPresent(alertType -> alertCounts.computeIfAbsent(key, ignored -> new EnumMap<>(MonthlyInvoiceSimulationAlertType.class)).merge(alertType, 1, Integer::sum));
        }

        List<MonthlyInvoiceSimulationAlert> alertsOf(Long runId, Map<OfferingKey, Money> previousTotals) {
            List<MonthlyInvoiceSimulationAlert> alerts = new ArrayList<>();
            alertCounts.forEach((key, byType) -> byType.forEach((alertType, affected) ->
                    alerts.add(MonthlyInvoiceSimulationAlert.raiseAlert(runId, alertType, key.type(), key.offeringId(), affected, messageOf(alertType, affected)))));
            installmentTotals.forEach((key, current) -> {
                Money previous = previousTotals.get(key);
                if (previous != null && previous.currency() == current.currency() && AtypicalInstallmentIncreaseRule.isAtypicalIncrease(previous, current)) {
                    alerts.add(MonthlyInvoiceSimulationAlert.raiseAlert(runId, MonthlyInvoiceSimulationAlertType.ATYPICAL_INCREASE, key.type(), key.offeringId(), 1,
                            "El total de cuotas pasa de " + previous.amount().toPlainString() + " a " + current.amount().toPlainString() + " " + current.currency()
                                    + " (más de 15% contra el mes anterior)"));
                }
            });
            return alerts;
        }

        private static String messageOf(MonthlyInvoiceSimulationAlertType alertType, int affected) {
            return switch (alertType) {
                case YEAR_NOT_CONFIGURED -> "No se emite: falta configurar el año lectivo del periodo (" + affected + " inscripciones)";
                case PRICE_MISSING -> "Falta el precio vigente para el periodo (" + affected + " inscripciones)";
                case EMISSION_ERROR -> "La emisión fallaría para " + affected + " inscripciones";
                case ATYPICAL_INCREASE -> "Aumento atípico del total de cuotas";
            };
        }
    }
}
