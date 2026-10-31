package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.operation.DraftInvoiceBatchApprover;
import com.red_escuela.backend.billing.invoice.operation.DraftInvoiceBatchDiscarder;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotAwaitingReviewException;
import com.red_escuela.backend.billing.run.exception.MonthlyInvoiceRunNotFoundException;
import com.red_escuela.backend.billing.run.repository.DraftInvoiceLineRepository;
import com.red_escuela.backend.billing.run.repository.MonthlyInvoiceRunRepository;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Service
public class MonthlyRunDraftReviewer {
    private static final Logger log = LoggerFactory.getLogger(MonthlyRunDraftReviewer.class);

    private final MonthlyInvoiceRunRepository runRepository;
    private final DraftInvoiceLineRepository draftRepository;
    private final DraftInvoiceBatchApprover approver;
    private final DraftInvoiceBatchDiscarder discarder;
    private final Clock clock;

    public MonthlyRunDraftReviewer(MonthlyInvoiceRunRepository runRepository, DraftInvoiceLineRepository draftRepository, DraftInvoiceBatchApprover approver,
                                   DraftInvoiceBatchDiscarder discarder, Clock clock) {
        this.runRepository = runRepository;
        this.draftRepository = draftRepository;
        this.approver = approver;
        this.discarder = discarder;
        this.clock = clock;
    }

    @Transactional
    public List<Invoice> approveDraftsOfRun(Long monthlyRunId, Collection<Long> invoiceIds, StatusChangeActor actor) {
        MonthlyInvoiceRun run = lockRunAwaitingReview(monthlyRunId);
        List<Invoice> approved = approver.approveDraftsOfRun(monthlyRunId, invoiceIds, LocalDate.now(clock), actor);
        completeRunWhenNoDraftsRemain(run);
        return approved;
    }

    @Transactional
    public List<Invoice> discardDraftsOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String reason, StatusChangeActor actor) {
        MonthlyInvoiceRun run = lockRunAwaitingReview(monthlyRunId);
        List<Invoice> discarded = discarder.discardDraftsOfRun(monthlyRunId, invoiceIds, reason, actor);
        completeRunWhenNoDraftsRemain(run);
        return discarded;
    }

    private MonthlyInvoiceRun lockRunAwaitingReview(Long monthlyRunId) {
        MonthlyInvoiceRun run = runRepository.findByIdForUpdate(monthlyRunId).filter(candidate -> candidate.getMode() == MonthlyInvoiceRunMode.REAL)
                .orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
        if (run.getStatus() != MonthlyInvoiceRunStatus.AWAITING_REVIEW) {
            throw new MonthlyInvoiceRunNotAwaitingReviewException(monthlyRunId, run.getStatus().name());
        }
        return run;
    }

    private void completeRunWhenNoDraftsRemain(MonthlyInvoiceRun run) {
        if (draftRepository.countDraftInvoicesOfRun(run.getId()) == 0) {
            run.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_REVIEW_COMPLETED, LocalDateTime.now(clock));
            runRepository.save(run);
            log.info("Corrida mensual sin borradores pendientes: corrida={}, estado={}", run.getId(), run.getStatus());
        }
    }
}
