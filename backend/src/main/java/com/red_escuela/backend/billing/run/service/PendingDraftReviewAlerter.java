package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.academicstructure.api.BusinessDayCalendar;
import com.red_escuela.backend.billing.run.domain.DraftReviewAlertSchedule;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.repository.DraftInvoiceLineRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.function.Predicate;

@Service
public class PendingDraftReviewAlerter {
    private static final Logger log = LoggerFactory.getLogger(PendingDraftReviewAlerter.class);

    private final MonthlyInvoiceRunRepository runRepository;
    private final DraftInvoiceLineRepository draftRepository;
    private final BusinessDayCalendar businessDayCalendar;
    private final BillingRunAlertPublisher alertPublisher;

    public PendingDraftReviewAlerter(MonthlyInvoiceRunRepository runRepository, DraftInvoiceLineRepository draftRepository, BusinessDayCalendar businessDayCalendar,
                                     BillingRunAlertPublisher alertPublisher) {
        this.runRepository = runRepository;
        this.draftRepository = draftRepository;
        this.businessDayCalendar = businessDayCalendar;
        this.alertPublisher = alertPublisher;
    }

    @Transactional
    public int alertRunsWithDraftsStillAwaitingReview(LocalDate businessDate) {
        Predicate<LocalDate> isBusinessDay = businessDayCalendar::isBusinessDay;
        int alerted = 0;
        for (MonthlyInvoiceRun run : runRepository.findByModeAndStatusOrderByIdAsc(MonthlyInvoiceRunMode.REAL, MonthlyInvoiceRunStatus.AWAITING_REVIEW)) {
            if (DraftReviewAlertSchedule.alertsOn(run.getPeriod(), businessDate, isBusinessDay)) {
                alerted += alertWhenDraftsRemain(run, businessDate);
            }
        }
        return alerted;
    }

    private int alertWhenDraftsRemain(MonthlyInvoiceRun run, LocalDate businessDate) {
        long pendingDrafts = draftRepository.countDraftInvoicesOfRun(run.getId());
        if (pendingDrafts == 0) {
            return 0;
        }
        log.warn("Borradores sin aprobar: corrida={}, periodo={}, pendientes={}", run.getId(), run.getPeriod(), pendingDrafts);
        alertPublisher.publishDraftsAwaitingReviewAlert(run.getId(), run.getPeriod(), businessDate, pendingDrafts);
        return 1;
    }
}
