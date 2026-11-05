package com.red_escuela.backend.billing.invoice.service;

import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuanceDestination;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingContext;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingRequest;
import com.red_escuela.backend.billing.invoice.domain.issuing.InvoiceIssuingResult;
import com.red_escuela.backend.billing.invoice.exception.DuplicateInvoiceChargeException;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingContextLoader;
import com.red_escuela.backend.billing.invoice.operation.InvoiceIssuingOperation;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.policy.PolicyVerdict;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.List;

@Service
public class InvoiceIssuingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceIssuingService.class);
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHARGE_KEY_CONSTRAINT = "uk_invoice_line_charge_key";
    private static final String ALREADY_ISSUED_CODE = "ALREADY_ISSUED";

    private final InvoiceIssuingContextLoader contextLoader;
    private final PolicySet<InvoiceIssuingContext> issuingPolicies;
    private final BillingOperationPipeline pipeline;
    private final InvoiceIssuingOperation operation;
    private final InvoiceRepository invoiceRepository;
    private final TransactionTemplate ownTransaction;
    private final MeterRegistry meterRegistry;

    public InvoiceIssuingService(InvoiceIssuingContextLoader contextLoader, PolicySet<InvoiceIssuingContext> issuingPolicies,
                                         BillingOperationPipeline pipeline, InvoiceIssuingOperation operation, InvoiceRepository invoiceRepository,
                                         PlatformTransactionManager transactionManager, MeterRegistry meterRegistry) {
        this.contextLoader = contextLoader;
        this.issuingPolicies = issuingPolicies;
        this.pipeline = pipeline;
        this.operation = operation;
        this.invoiceRepository = invoiceRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.meterRegistry = meterRegistry;
    }

    public InvoiceIssuingResult issueInvoiceInNewTransaction(InvoiceIssuingRequest request) {
        return issueInvoiceInNewTransaction(request, InvoiceIssuanceDestination.EMIT_NOW);
    }

    public InvoiceIssuingResult issueInvoiceInNewTransaction(InvoiceIssuingRequest request, InvoiceIssuanceDestination destination) {
        try {
            return ownTransaction.execute(status -> issueInvoice(request, destination));
        } catch (DataIntegrityViolationException e) {
            if (!violatesChargeKeyUniqueness(e)) {
                throw e;
            }
            log.warn("Cargo duplicado detectado por la base: tipo={}, periodo={}, motivo={}", request.feeType(), request.period(), request.issuingReason());
            return invoiceRepository.findLiveInvoiceIdByChargeKey(request.chargeKey().value())
                    .<InvoiceIssuingResult>map(InvoiceIssuingResult.InvoiceAlreadyIssued::new)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public InvoiceIssuingResult issueInvoiceInCallerTransaction(InvoiceIssuingRequest request) {
        try {
            return issueInvoice(request, InvoiceIssuanceDestination.EMIT_NOW);
        } catch (DataIntegrityViolationException e) {
            if (!violatesChargeKeyUniqueness(e)) {
                throw e;
            }
            duplicateChargeEvaluation().throwIfDenied(DuplicateInvoiceChargeException::new);
            throw e;
        }
    }

    private InvoiceIssuingResult issueInvoice(InvoiceIssuingRequest request, InvoiceIssuanceDestination destination) {
        InvoiceIssuingContext context = contextLoader.loadContextLockingRecipient(request);
        context.directTo(destination);
        PolicyEvaluationResult evaluation = issuingPolicies.evaluateRulesUntilFirstViolation(context);
        if (!evaluation.allowed()) {
            return resultOfRejection(context, evaluation);
        }
        InvoiceIssuingResult.InvoiceIssued issued = pipeline.runOperation(operation, context)
                .resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException("Invoice issuing does not wait for authorization: " + requestId));
        recordIssuance(issued, request, destination);
        return issued;
    }

    private void recordIssuance(InvoiceIssuingResult.InvoiceIssued issued, InvoiceIssuingRequest request, InvoiceIssuanceDestination destination) {
        if (destination instanceof InvoiceIssuanceDestination.DraftOfMonthlyRun draftOfRun) {
            log.info("Borrador creado: factura={}, corrida={}, tipo={}, periodo={}, motivo={}", issued.invoice().getId(), draftOfRun.monthlyInvoiceRunId(),
                    issued.invoice().getInvoiceType(), request.period(), request.issuingReason());
            meterRegistry.counter("billing_invoice_draft_created_total", "reason", request.issuingReason().name(), "invoiceType", request.invoiceType().name()).increment();
            return;
        }
        log.info("Factura emitida: factura={}, numero={}, tipo={}, periodo={}, vencimiento={}, motivo={}", issued.invoice().getId(),
                issued.invoice().getInvoiceNumber().value(), issued.invoice().getInvoiceType(), request.period(), request.dueDate(), request.issuingReason());
        meterRegistry.counter("billing_invoice_issued_total", "reason", request.issuingReason().name(), "invoiceType", request.invoiceType().name()).increment();
    }

    private InvoiceIssuingResult resultOfRejection(InvoiceIssuingContext context, PolicyEvaluationResult evaluation) {
        InvoiceIssuingRequest request = context.request();
        boolean alreadyIssued = context.chargeKeyAlreadyUsed()
                && evaluation.firstDenial().map(verdict -> ALREADY_ISSUED_CODE.equals(verdict.code())).orElse(false);
        if (alreadyIssued) {
            log.info("Cargo ya emitido: factura={}, tipo={}, periodo={}, motivo={}", context.facts().existingInvoiceId(), request.feeType(), request.period(),
                    request.issuingReason());
            return new InvoiceIssuingResult.InvoiceAlreadyIssued(context.facts().existingInvoiceId());
        }
        log.warn("Cargo rechazado: tipo={}, periodo={}, motivo={}, codigos={}", request.feeType(), request.period(), request.issuingReason(),
                evaluation.describeDenials());
        evaluation.denials().forEach(verdict -> meterRegistry.counter("billing_invoice_rejected_total", "reason", request.issuingReason().name(),
                "code", verdict.code()).increment());
        return new InvoiceIssuingResult.InvoiceIssuingRejected(evaluation);
    }

    private PolicyEvaluationResult duplicateChargeEvaluation() {
        return new PolicyEvaluationResult(List.of(PolicyVerdict.deny("ChargeKeyNotAlreadyUsedPolicy", ALREADY_ISSUED_CODE, "El cargo ya fue emitido")));
    }

    private static boolean violatesChargeKeyUniqueness(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(CHARGE_KEY_CONSTRAINT)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
