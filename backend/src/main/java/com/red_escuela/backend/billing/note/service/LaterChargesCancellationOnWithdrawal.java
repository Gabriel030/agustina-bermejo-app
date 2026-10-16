package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.SystemCancellationOrigin;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationPlan;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationPlanner;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.enrollment.api.WithdrawalCause;
import com.red_escuela.backend.enrollment.api.WithdrawnEnrollmentFacts;
import org.springframework.stereotype.Service;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

@Service
public class LaterChargesCancellationOnWithdrawal {
    private static final Logger log = LoggerFactory.getLogger(LaterChargesCancellationOnWithdrawal.class);
    private static final String DRAFT_DISCARD_PROCESS = "WITHDRAWAL_DRAFT_DISCARD";
    private static final String DRAFT_DISCARD_REASON = "Baja: período posterior a la baja";
    private static final String FEE_CANCELLATION_JUSTIFICATION = "Baja: la matrícula del año siguiente no se cobra";

    private final WithdrawalInvoiceAccess invoiceAccess;
    private final CancelInvoiceOperation cancelInvoiceOperation;
    private final BillingOperationPipeline pipeline;
    private final CancelInvoicesAfterWithdrawalOperation operation;
    private final CreditNoteActorResolver actorResolver;
    private final StudentCurrentAccountService accountService;

    public LaterChargesCancellationOnWithdrawal(WithdrawalInvoiceAccess invoiceAccess, CancelInvoiceOperation cancelInvoiceOperation,
                                                 BillingOperationPipeline pipeline, CancelInvoicesAfterWithdrawalOperation operation,
                                                 CreditNoteActorResolver actorResolver, StudentCurrentAccountService accountService) {
        this.invoiceAccess = invoiceAccess;
        this.cancelInvoiceOperation = cancelInvoiceOperation;
        this.pipeline = pipeline;
        this.operation = operation;
        this.actorResolver = actorResolver;
        this.accountService = accountService;
    }

    @Transactional
    public void cancelLaterChargesOf(WithdrawnEnrollmentFacts facts) {
        if (facts.cause() != WithdrawalCause.VOLUNTARY) {
            return;
        }
        List<Invoice> invoices = invoiceAccess.findInvoicesOfEnrollment(facts.programEnrollmentId(), facts.courseEnrollmentId());
        invoices.stream().map(Invoice::getStudentCurrentAccountId).distinct().sorted().forEach(accountService::lockStudentCurrentAccount);
        Set<Long> withPendingRequest = invoiceAccess.findInvoiceIdsWithPendingAuthorizationRequest(invoices.stream().map(Invoice::getId).toList());
        WithdrawalCancellationPlan plan = new WithdrawalCancellationPlanner(YearMonth.from(facts.effectiveDate()), facts.registeredOn(), invoiceAccess::findTermStartOfTemplate)
                .planFor(invoices, withPendingRequest);
        if (plan.isEmpty()) {
            return;
        }
        invoiceAccess.discardDraftInvoices(plan.draftInvoiceIds(), DRAFT_DISCARD_REASON, StatusChangeActor.ofProcess(DRAFT_DISCARD_PROCESS));
        if (!plan.requestItems().isEmpty()) {
            pipeline.runOperation(operation, new CancelInvoicesAfterWithdrawalCommand(plan.requestItems(), facts.effectiveDate(), actorResolver.currentPersonId(),
                    actorResolver.currentActorIsDirector(), null));
        }
        plan.automaticInvoiceIds().forEach(invoiceId -> pipeline.runOperation(cancelInvoiceOperation,
                CancelInvoiceCommand.bySystem(invoiceId, SystemCancellationOrigin.FUTURE_ENROLLMENT_FEE_ON_WITHDRAWAL, FEE_CANCELLATION_JUSTIFICATION)));
        log.info("Cargos posteriores a la baja: inscripcionPrograma={}, inscripcionCurso={}, pedido={}, matriculasCanceladas={}, borradoresDescartados={}",
                facts.programEnrollmentId(), facts.courseEnrollmentId(), plan.requestItems().size(), plan.automaticInvoiceIds().size(), plan.draftInvoiceIds().size());
    }
}
