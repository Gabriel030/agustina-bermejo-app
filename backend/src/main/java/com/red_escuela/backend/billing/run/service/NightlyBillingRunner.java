package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.events.NightlyBillingRunFailedEvent;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunClosing;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.operation.AbstractBillingRunStep;
import com.red_escuela.backend.billing.run.policy.PreviousStepContext;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NightlyBillingRunner {
    private static final Logger log = LoggerFactory.getLogger(NightlyBillingRunner.class);

    private final BillingRunStepSequence stepSequence;
    private final BillingRunRecorder recorder;
    private final PolicySet<PreviousStepContext> stepSequencePolicySet;
    private final ApplicationEventPublisher eventPublisher;

    public NightlyBillingRunner(BillingRunStepSequence stepSequence, BillingRunRecorder recorder, PolicySet<PreviousStepContext> nightlyBillingStepSequencePolicySet,
                                ApplicationEventPublisher eventPublisher) {
        this.stepSequence = stepSequence;
        this.recorder = recorder;
        this.stepSequencePolicySet = nightlyBillingStepSequencePolicySet;
        this.eventPublisher = eventPublisher;
    }

    public Optional<Long> runNightlyBillingSteps(LocalDate businessDate, NightlyBillingTrigger trigger) {
        Optional<Long> nightlyRunId = openNightlyRunUnlessAlreadyRegistered(businessDate, trigger);
        nightlyRunId.ifPresentOrElse(id -> runStepsOfNightlyRun(id, businessDate),
                () -> log.info("Proceso nocturno ignorado: ya existe la corrida de la fecha {}", businessDate));
        return nightlyRunId;
    }

    private Optional<Long> openNightlyRunUnlessAlreadyRegistered(LocalDate businessDate, NightlyBillingTrigger trigger) {
        return Optional.of(businessDate)
                .filter(date -> !recorder.isNightlyRunRegisteredFor(date))
                .flatMap(date -> openNightlyRunTolerantToRace(date, trigger));
    }

    private Optional<Long> openNightlyRunTolerantToRace(LocalDate businessDate, NightlyBillingTrigger trigger) {
        try {
            return Optional.of(recorder.openNightlyRun(businessDate, trigger));
        } catch (DataIntegrityViolationException e) {
            return Optional.empty();
        }
    }

    private void runStepsOfNightlyRun(Long nightlyRunId, LocalDate businessDate) {
        log.info("Proceso nocturno iniciado: fecha={}", businessDate);
        long start = System.currentTimeMillis();
        List<BillingRunStatus> stepStatuses = new ArrayList<>();
        PreviousStepContext previous = PreviousStepContext.beforeTheFirstStep();
        for (AbstractBillingRunStep step : stepSequence.orderedSteps()) {
            if (!stepSequencePolicySet.evaluateAllRules(previous).allowed()) {
                break;
            }
            BillingRunStatus stepStatus = runOneStep(nightlyRunId, step, businessDate);
            stepStatuses.add(stepStatus);
            previous = new PreviousStepContext(stepStatus);
        }
        recorder.finishNightlyRun(nightlyRunId, NightlyBillingRunClosing.closingEventFor(stepStatuses));
        log.info("Proceso nocturno finalizado: fecha={}, pasos={}, duracion={}ms", businessDate, stepStatuses, System.currentTimeMillis() - start);
    }

    private BillingRunStatus runOneStep(Long nightlyRunId, AbstractBillingRunStep step, LocalDate businessDate) {
        return step.findNotApplicableCode()
                .map(code -> recorder.declareStepNotApplicable(nightlyRunId, step.step(), code))
                .orElseGet(() -> runApplicableStep(nightlyRunId, step, businessDate));
    }

    private BillingRunStatus runApplicableStep(Long nightlyRunId, AbstractBillingRunStep step, LocalDate businessDate) {
        Long stepRunId = recorder.openStepRun(nightlyRunId, step.step());
        try {
            BillingRunStepOutcome outcome = step.runStepForBusinessDate(businessDate);
            return recorder.finishStepRun(stepRunId, outcome);
        } catch (RuntimeException e) {
            log.error("Paso nocturno {} falló entero: fecha={}", step.step(), businessDate, e);
            BillingRunStatus failedStatus = recorder.abortStepRun(stepRunId);
            eventPublisher.publishEvent(NightlyBillingRunFailedEvent.failedAt(nightlyRunId, businessDate, step.step().name(), e.getMessage()));
            return failedStatus;
        }
    }
}
