package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunStatus;
import com.red_escuela.backend.billing.run.policy.NightlyBillingCompletedTodayContext;
import com.red_escuela.backend.billing.run.repository.BillingRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class MissedBillingRunDetector {
    private static final Logger log = LoggerFactory.getLogger(MissedBillingRunDetector.class);
    private static final String UNKNOWN_STEP = "UNKNOWN";

    private final NightlyBillingRunRepository nightlyRunRepository;
    private final BillingRunRepository stepRunRepository;
    private final PolicySet<NightlyBillingCompletedTodayContext> completedTodayPolicySet;
    private final BillingRunAlertPublisher alertPublisher;

    public MissedBillingRunDetector(NightlyBillingRunRepository nightlyRunRepository, BillingRunRepository stepRunRepository,
                                    PolicySet<NightlyBillingCompletedTodayContext> nightlyBillingCompletedTodayPolicySet, BillingRunAlertPublisher alertPublisher) {
        this.nightlyRunRepository = nightlyRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.completedTodayPolicySet = nightlyBillingCompletedTodayPolicySet;
        this.alertPublisher = alertPublisher;
    }

    @Transactional
    public void alertWhenTheNightlyRunOfTodayIsNotCompleted(LocalDate businessDate) {
        Optional<NightlyBillingRun> runOfToday = nightlyRunRepository.findByBusinessDate(businessDate);
        NightlyBillingCompletedTodayContext context = runOfToday
                .map(run -> new NightlyBillingCompletedTodayContext(businessDate, run.getBusinessDate(), run.getStatus()))
                .orElseGet(() -> NightlyBillingCompletedTodayContext.withoutAnyRun(businessDate));
        Optional.of(context).filter(c -> !completedTodayPolicySet.evaluateAllRules(c).allowed())
                .ifPresent(c -> runOfToday.ifPresentOrElse(run -> alertForUncompletedRun(run, businessDate), () -> alertPublisher.publishRunMissedAlert(businessDate)));
    }

    private void alertForUncompletedRun(NightlyBillingRun run, LocalDate businessDate) {
        NightlyBillingRunStatus status = run.getStatus();
        switch (status) {
            case FAILED -> alertPublisher.publishRunFailedAlert(run.getId(), businessDate, findFailedStepName(run.getId()));
            case RUNNING -> log.warn("Proceso nocturno del {} todavía en ejecución a la hora del vigía", businessDate);
            case COMPLETED, COMPLETED_WITH_ERRORS -> { }
        }
    }

    private String findFailedStepName(Long nightlyRunId) {
        return stepRunRepository.findByNightlyBillingRunIdOrderByIdAsc(nightlyRunId).stream()
                .filter(step -> step.getStatus() == BillingRunStatus.FAILED)
                .findFirst().map(BillingRun::getStep).map(Enum::name).orElse(UNKNOWN_STEP);
    }
}
