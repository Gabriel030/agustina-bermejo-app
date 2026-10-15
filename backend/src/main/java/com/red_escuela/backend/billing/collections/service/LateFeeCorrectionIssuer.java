package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.domain.AppliedLateFeeCorrection;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeCalculationStrategy;
import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import com.red_escuela.backend.billing.collections.domain.LateFeeFreeze;
import com.red_escuela.backend.billing.collections.domain.LateFeeWithdrawalCorrectionCalculator;
import com.red_escuela.backend.billing.collections.domain.PlannedLateFeeCorrection;
import com.red_escuela.backend.billing.collections.operation.IssueLateFeeCorrectionCommand;
import com.red_escuela.backend.billing.collections.operation.LateFeeCorrectionCreditNoteOperation;
import com.red_escuela.backend.billing.collections.policy.LateFeeCorrectionContext;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeCorrectionRepository;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentTerms;
import com.red_escuela.backend.billing.configuration.domain.LatePaymentVersion;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLine;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class LateFeeCorrectionIssuer {
    private static final Logger log = LoggerFactory.getLogger(LateFeeCorrectionIssuer.class);
    private static final Set<String> UNIQUENESS_SQL_STATES = Set.of("23505", "23P01");
    private static final Set<String> CORRECTION_UNIQUE_CONSTRAINTS = Set.of("uk_late_fee_charge_correction_credit_note", "ex_late_fee_charge_correction_no_overlapping_days");

    private final LateFeeChargeRepository chargeRepository;
    private final LateFeeChargeCorrectionRepository correctionRepository;
    private final InvoiceCreditAccessService invoiceAccess;
    private final StudentCurrentAccountService accountService;
    private final LatePaymentVersionResolver versionResolver;
    private final LateFeeFreezeResolver freezeResolver;
    private final PolicySet<LateFeeCorrectionContext> correctionPolicies;
    private final BillingOperationPipeline pipeline;
    private final LateFeeCorrectionCreditNoteOperation operation;
    private final LateFeeWithdrawalCorrectionCalculator calculator;
    private final TransactionTemplate ownTransaction;

    public LateFeeCorrectionIssuer(LateFeeChargeRepository chargeRepository, LateFeeChargeCorrectionRepository correctionRepository,
                                   InvoiceCreditAccessService invoiceAccess, StudentCurrentAccountService accountService, LatePaymentVersionResolver versionResolver,
                                   LateFeeFreezeResolver freezeResolver, PolicySet<LateFeeCorrectionContext> lateFeeCorrectionPolicySet,
                                   BillingOperationPipeline pipeline, LateFeeCorrectionCreditNoteOperation operation, LateFeeCalculationStrategy calculationStrategy,
                                   PlatformTransactionManager transactionManager) {
        this.chargeRepository = chargeRepository;
        this.correctionRepository = correctionRepository;
        this.invoiceAccess = invoiceAccess;
        this.accountService = accountService;
        this.versionResolver = versionResolver;
        this.freezeResolver = freezeResolver;
        this.correctionPolicies = lateFeeCorrectionPolicySet;
        this.pipeline = pipeline;
        this.operation = operation;
        this.calculator = new LateFeeWithdrawalCorrectionCalculator(calculationStrategy);
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    public LateFeeCorrectionOutcome correctLateFeeChargeInNewTransaction(Long lateFeeChargeId, LocalDate factDate) {
        try {
            return ownTransaction.execute(status -> correctLateFeeChargeUnderLocks(lateFeeChargeId, factDate));
        } catch (DataIntegrityViolationException e) {
            if (!violatesCorrectionUniqueness(e)) {
                throw e;
            }
            log.warn("Corrección de recargo duplicada detectada por la base: cargo={}", lateFeeChargeId);
            return LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
        }
    }

    private LateFeeCorrectionOutcome correctLateFeeChargeUnderLocks(Long lateFeeChargeId, LocalDate factDate) {
        LateFeeCharge charge = chargeRepository.findById(lateFeeChargeId).orElseThrow(() -> new IllegalStateException("Late fee charge not found: " + lateFeeChargeId));
        accountService.lockStudentCurrentAccount(charge.getStudentCurrentAccountId());
        Invoice debitNote = invoiceAccess.lockInvoice(charge.getDebitNoteId()).orElseThrow(() -> new InvoiceNotFoundException(charge.getDebitNoteId()));
        Invoice origin = invoiceAccess.findInvoice(charge.getOriginInvoiceId()).orElseThrow(() -> new InvoiceNotFoundException(charge.getOriginInvoiceId()));
        Optional<LatePaymentVersion> dueDateVersion = versionResolver.findVersionInEffectOnOrFirst(origin.getDueDate(), origin.getId());
        if (dueDateVersion.isEmpty()) {
            return LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
        }
        LatePaymentTerms dueDateTerms = dueDateVersion.get().terms();
        LatePaymentTimeline timeline = versionResolver.timelineUpTo(charge.getPeriodEnd());
        InvoiceLine originLine = origin.getPrimaryLine();
        LateFeeFreeze freeze = freezeResolver.resolveFreezeOfEnrollment(originLine.getProgramEnrollmentId(), originLine.getCourseEnrollmentId(), dueDateTerms, timeline);
        if (freeze.enrollmentEndDate().isEmpty()) {
            return LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
        }
        List<AppliedLateFeeCorrection> applied = correctionRepository.findByLateFeeChargeIdOrderByFirstCorrectedDay(lateFeeChargeId).stream()
                .map(LateFeeCorrectionIssuer::appliedOf).toList();
        Optional<PlannedLateFeeCorrection> planned = calculator.planCorrectionOfDebitNote(charge.toSnapshot(), applied, freeze.enrollmentEndDate().get(),
                freeze.frozenByRuleOfTheEndDate(), day -> timeline.termsOn(day, dueDateTerms).lateFeeChargingEnabled());
        if (planned.isEmpty()) {
            return LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
        }
        PolicyEvaluationResult evaluation = correctionPolicies.evaluateRulesUntilFirstViolation(new LateFeeCorrectionContext(debitNote.getId(), debitNote.getDocumentType(),
                debitNote.getInvoiceType(), debitNote.getDocumentStatus(), debitNote.getPaymentStatus()));
        if (!evaluation.allowed()) {
            log.warn("Corrección de recargo diferida hasta que se decida el comprobante o se resuelva la causa: nota de débito={}, codigos={}", debitNote.getInvoiceNumber().value(), evaluation.describeDenials());
            return debitNote.getPaymentStatus() == InvoicePaymentStatus.PAYMENT_UNDER_REVIEW ? LateFeeCorrectionOutcome.DEFERRED : LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
        }
        IssueLateFeeCorrectionCommand command = new IssueLateFeeCorrectionCommand(lateFeeChargeId, debitNote.getId(), charge.getStudentCurrentAccountId(), planned.get(),
                freeze.enrollmentEndDate().get(), factDate);
        return pipeline.runOperation(operation, command).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException(
                "Late fee correction does not wait for authorization: " + requestId)).isPresent()
                ? LateFeeCorrectionOutcome.CORRECTED : LateFeeCorrectionOutcome.NOTHING_TO_CORRECT;
    }

    private static AppliedLateFeeCorrection appliedOf(LateFeeChargeCorrection correction) {
        return new AppliedLateFeeCorrection(new LateFeeAccrualPeriod(correction.getFirstCorrectedDay(), correction.getLastCorrectedDay()), correction.getCorrectedDays());
    }

    private static boolean violatesCorrectionUniqueness(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUENESS_SQL_STATES.contains(sql.getSQLState()) && sql.getMessage() != null
                    && CORRECTION_UNIQUE_CONSTRAINTS.stream().anyMatch(sql.getMessage()::contains)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
