package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.operation.LateFeeDebitNoteIssuingOperation;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@Service
public class LateFeeIssuer {
    private static final Logger log = LoggerFactory.getLogger(LateFeeIssuer.class);
    private static final String UNIQUE_VIOLATION = "23505";
    private static final Set<String> LATE_FEE_UNIQUE_CONSTRAINTS = Set.of("uk_invoice_line_charge_key", "uk_late_fee_charge_origin_month", "uk_late_fee_charge_debit_note");

    private final InvoiceCreditAccessService invoiceAccess;
    private final StudentCurrentAccountService accountService;
    private final LateFeeChargeRepository chargeRepository;
    private final LateFeeContextLoader contextLoader;
    private final PolicySet<LateFeeIssuingContext> issuingPolicies;
    private final BillingOperationPipeline pipeline;
    private final LateFeeDebitNoteIssuingOperation operation;
    private final TransactionTemplate ownTransaction;

    public LateFeeIssuer(InvoiceCreditAccessService invoiceAccess, StudentCurrentAccountService accountService,
                         LateFeeChargeRepository chargeRepository, LateFeeContextLoader contextLoader, PolicySet<LateFeeIssuingContext> debitNoteIssuingPolicySet,
                         BillingOperationPipeline pipeline, LateFeeDebitNoteIssuingOperation operation, PlatformTransactionManager transactionManager) {
        this.invoiceAccess = invoiceAccess;
        this.accountService = accountService;
        this.chargeRepository = chargeRepository;
        this.contextLoader = contextLoader;
        this.issuingPolicies = debitNoteIssuingPolicySet;
        this.pipeline = pipeline;
        this.operation = operation;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public boolean issueLateFeeOfMonthInNewTransaction(Long originInvoiceId, int monthOrdinal, LocalDate businessDate) {
        try {
            return Boolean.TRUE.equals(ownTransaction.execute(status -> issueLateFeeUnderLocks(originInvoiceId, monthOrdinal, businessDate)));
        } catch (DataIntegrityViolationException e) {
            if (!violatesLateFeeUniqueness(e)) {
                throw e;
            }
            log.warn("Recargo duplicado detectado por la base: origen={}, mes={}", originInvoiceId, monthOrdinal);
            return false;
        }
    }

    private boolean issueLateFeeUnderLocks(Long originInvoiceId, int monthOrdinal, LocalDate businessDate) {
        Long accountId = invoiceAccess.findAccountIdOf(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
        accountService.lockStudentCurrentAccount(accountId);
        Invoice origin = invoiceAccess.lockInvoice(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
        Set<Integer> charged = chargeRepository.findMonthOrdinalsByOriginInvoiceId(originInvoiceId);
        Optional<LateFeeIssuingContext> context = contextLoader.loadMissingMonthContexts(origin, charged, businessDate).stream()
                .filter(candidate -> candidate.month().ordinal() == monthOrdinal).findFirst();
        if (context.isEmpty()) {
            return false;
        }
        PolicyEvaluationResult evaluation = issuingPolicies.evaluateRulesUntilFirstViolation(context.get());
        if (!evaluation.allowed() || context.get().lateFeeAmount().value().isZero()) {
            log.debug("Recargo no emitido: origen={}, mes={}, codigos={}", originInvoiceId, monthOrdinal, evaluation.describeDenials());
            return false;
        }
        pipeline.runOperation(operation, context.get()).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException(
                "Late fee issuing does not wait for authorization: " + requestId));
        return true;
    }

    private static boolean violatesLateFeeUniqueness(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState()) && sql.getMessage() != null
                    && LATE_FEE_UNIQUE_CONSTRAINTS.stream().anyMatch(sql.getMessage()::contains)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
