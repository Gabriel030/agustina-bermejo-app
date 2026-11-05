package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.run.domain.BillingRun;
import com.red_escuela.backend.billing.run.domain.BillingRunItem;
import com.red_escuela.backend.billing.run.domain.BillingRunStatus;
import com.red_escuela.backend.billing.run.domain.BillingRunStep;
import com.red_escuela.backend.billing.run.domain.BillingRunStepOutcome;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRun;
import com.red_escuela.backend.billing.run.domain.NightlyBillingRunEvent;
import com.red_escuela.backend.billing.run.domain.NightlyBillingTrigger;
import com.red_escuela.backend.billing.run.repository.BillingRunItemRepository;
import com.red_escuela.backend.billing.run.repository.BillingRunRepository;
import com.red_escuela.backend.billing.run.repository.NightlyBillingRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class BillingRunRecorder {
    private final NightlyBillingRunRepository nightlyRunRepository;
    private final BillingRunRepository stepRunRepository;
    private final BillingRunItemRepository itemRepository;
    private final Clock clock;

    public BillingRunRecorder(NightlyBillingRunRepository nightlyRunRepository, BillingRunRepository stepRunRepository, BillingRunItemRepository itemRepository, Clock clock) {
        this.nightlyRunRepository = nightlyRunRepository;
        this.stepRunRepository = stepRunRepository;
        this.itemRepository = itemRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isNightlyRunRegisteredFor(LocalDate businessDate) {
        return nightlyRunRepository.existsByBusinessDate(businessDate);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long openNightlyRun(LocalDate businessDate, NightlyBillingTrigger trigger) {
        return nightlyRunRepository.saveAndFlush(NightlyBillingRun.startNightlyRun(businessDate, trigger, LocalDateTime.now(clock))).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long openStepRun(Long nightlyRunId, BillingRunStep step) {
        return stepRunRepository.save(BillingRun.startStepRun(nightlyRunId, step, LocalDateTime.now(clock))).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunStatus declareStepNotApplicable(Long nightlyRunId, BillingRunStep step, String notApplicableCode) {
        return stepRunRepository.save(BillingRun.declareStepNotApplicable(nightlyRunId, step, notApplicableCode, LocalDateTime.now(clock))).getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunStatus finishStepRun(Long stepRunId, BillingRunStepOutcome outcome) {
        BillingRun stepRun = stepRunRepository.findById(stepRunId).orElseThrow();
        stepRun.finishStepRun(outcome, LocalDateTime.now(clock));
        itemRepository.saveAll(outcome.items().stream().map(item -> BillingRunItem.recordItemOf(stepRunId, item)).toList());
        return stepRun.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BillingRunStatus abortStepRun(Long stepRunId) {
        BillingRun stepRun = stepRunRepository.findById(stepRunId).orElseThrow();
        stepRun.abortStepRun(LocalDateTime.now(clock));
        return stepRun.getStatus();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finishNightlyRun(Long nightlyRunId, NightlyBillingRunEvent closingEvent) {
        nightlyRunRepository.findById(nightlyRunId).orElseThrow().finishNightlyRun(closingEvent, LocalDateTime.now(clock));
    }
}
