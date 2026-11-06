package com.red_escuela.backend.billing.run.service;

import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.issuing.DraftInvoiceApprovalCommand;
import com.red_escuela.backend.billing.invoice.operation.DraftInvoiceApprovalOperation;
import com.red_escuela.backend.billing.invoice.operation.DraftInvoiceBatchDiscarder;
import com.red_escuela.backend.billing.operation.AuthorizationPendingException;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRun;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunEvent;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunMode;
import com.red_escuela.backend.billing.run.domain.MonthlyInvoiceRunStatus;
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
    private final BillingOperationPipeline pipeline;
    private final DraftInvoiceApprovalOperation approval;
    private final DraftInvoiceBatchDiscarder discarder;
    private final Clock clock;

    public MonthlyRunDraftReviewer(MonthlyInvoiceRunRepository runRepository, DraftInvoiceLineRepository draftRepository, BillingOperationPipeline pipeline,
                                   DraftInvoiceApprovalOperation approval, DraftInvoiceBatchDiscarder discarder, Clock clock) {
        this.runRepository = runRepository;
        this.draftRepository = draftRepository;
        this.pipeline = pipeline;
        this.approval = approval;
        this.discarder = discarder;
        this.clock = clock;
    }

    @Transactional
    public List<Invoice> approveDraftsOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String idempotencyKey, StatusChangeActor actor) {
        MonthlyInvoiceRun run = lockRealRun(monthlyRunId);
        List<Invoice> approved = pipeline.runOperation(approval, new DraftInvoiceApprovalCommand(monthlyRunId, invoiceIds, LocalDate.now(clock), idempotencyKey, actor))
                .resultOrThrowIfAwaitingAuthorization(AuthorizationPendingException::new);
        completeRunWhenNoDraftsRemain(run);
        return approved;
    }

    @Transactional
    public List<Invoice> discardDraftsOfRun(Long monthlyRunId, Collection<Long> invoiceIds, String reason, StatusChangeActor actor) {
        MonthlyInvoiceRun run = lockRealRun(monthlyRunId);
        run.requireAwaitingReview();
        List<Invoice> discarded = discarder.discardDraftsOfRun(monthlyRunId, invoiceIds, reason, actor);
        completeRunWhenNoDraftsRemain(run);
        return discarded;
    }

    private MonthlyInvoiceRun lockRealRun(Long monthlyRunId) {
        return runRepository.findByIdForUpdate(monthlyRunId).filter(candidate -> candidate.getMode() == MonthlyInvoiceRunMode.REAL)
                .orElseThrow(() -> new MonthlyInvoiceRunNotFoundException(monthlyRunId));
    }

    private void completeRunWhenNoDraftsRemain(MonthlyInvoiceRun run) {
        if (run.getStatus() == MonthlyInvoiceRunStatus.AWAITING_REVIEW && draftRepository.countDraftInvoicesOfRun(run.getId()) == 0) {
            run.finishMonthlyRun(MonthlyInvoiceRunEvent.RUN_REVIEW_COMPLETED, LocalDateTime.now(clock));
            runRepository.save(run);
            log.info("Corrida mensual sin borradores pendientes: corrida={}, estado={}", run.getId(), run.getStatus());
        }
    }
}
