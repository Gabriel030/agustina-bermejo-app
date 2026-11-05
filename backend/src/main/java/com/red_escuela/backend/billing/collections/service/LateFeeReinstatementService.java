package com.red_escuela.backend.billing.collections.service;

import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import com.red_escuela.backend.billing.collections.operation.LateFeeDebitNoteIssuingOperation;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeCorrectionRepository;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentStatus;
import com.red_escuela.backend.billing.operation.AnnulledLateFeeReinstatement;
import com.red_escuela.backend.billing.operation.AnnulledLateFeeReinstatementPlanning;
import com.red_escuela.backend.billing.operation.PlannedLateFeeReinstatement;
import com.red_escuela.backend.billing.operation.BillingOperationPipeline;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluationResult;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class LateFeeReinstatementService implements AnnulledLateFeeReinstatement, AnnulledLateFeeReinstatementPlanning {
    private static final Logger log = LoggerFactory.getLogger(LateFeeReinstatementService.class);

    private final LateFeeChargeCorrectionRepository correctionRepository;
    private final InvoiceCreditAccessService invoiceAccess;
    private final LateFeeContextLoader contextLoader;
    private final LatePaymentVersionResolver versionResolver;
    private final PolicySet<LateFeeIssuingContext> issuingPolicies;
    private final BillingOperationPipeline pipeline;
    private final LateFeeDebitNoteIssuingOperation operation;

    public LateFeeReinstatementService(LateFeeChargeCorrectionRepository correctionRepository, InvoiceCreditAccessService invoiceAccess,
                                       LateFeeContextLoader contextLoader, LatePaymentVersionResolver versionResolver,
                                       PolicySet<LateFeeIssuingContext> debitNoteIssuingPolicySet, BillingOperationPipeline pipeline,
                                       LateFeeDebitNoteIssuingOperation operation) {
        this.correctionRepository = correctionRepository;
        this.invoiceAccess = invoiceAccess;
        this.contextLoader = contextLoader;
        this.versionResolver = versionResolver;
        this.issuingPolicies = debitNoteIssuingPolicySet;
        this.pipeline = pipeline;
        this.operation = operation;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Long> reinstateLateFeesAnnulledByTheReversedPayment(Collection<Long> reopenedInvoiceIds, LocalDate businessDate) {
        LatePaymentTimeline timeline = versionResolver.timelineUpTo(businessDate);
        List<Long> reinstatedDebitNoteIds = new ArrayList<>();
        reopenedInvoiceIds.stream().sorted().forEach(originInvoiceId -> {
            Invoice origin = invoiceAccess.findInvoice(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
            for (LateFeeChargeCorrection correction : correctionRepository.findPaymentCorrectionsNotReinstatedOfOriginInvoice(originInvoiceId)) {
                reinstateCorrection(origin, correction, businessDate, timeline).ifPresent(reinstatedDebitNoteIds::add);
            }
        });
        return reinstatedDebitNoteIds;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> findCorrectionIdsAwaitingReinstatement(Long originInvoiceId) {
        return correctionRepository.findPaymentCorrectionsNotReinstatedOfOriginInvoice(originInvoiceId).stream().map(LateFeeChargeCorrection::getId).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlannedLateFeeReinstatement> planReinstatement(Long originInvoiceId, Long correctionId, boolean originIsOverdue, LocalDate businessDate) {
        if (!originIsOverdue) {
            return Optional.empty();
        }
        Invoice origin = invoiceAccess.findInvoice(originInvoiceId).orElseThrow(() -> new InvoiceNotFoundException(originInvoiceId));
        LateFeeChargeCorrection correction = correctionRepository.findById(correctionId).orElseThrow();
        return findIssuableContext(origin, InvoicePaymentStatus.OVERDUE, correction, businessDate, versionResolver.timelineUpTo(businessDate), false).map(context -> new PlannedLateFeeReinstatement(
                originInvoiceId, correctionId, context.period().period().firstDay(), context.period().period().lastDay(), context.period().chargedDays(),
                context.lateFeeAmount(), context.issueDate(), context.debitNoteDueDate()));
    }

    private Optional<LateFeeIssuingContext> findIssuableContext(Invoice origin, InvoicePaymentStatus originStatus, LateFeeChargeCorrection correction, LocalDate businessDate,
                                                                 LatePaymentTimeline timeline, boolean logOmission) {
        Optional<LateFeeIssuingContext> context = contextLoader.loadReinstatementContext(origin, originStatus, correction, businessDate, timeline);
        if (context.isEmpty()) {
            if (logOmission) {
                log.warn("Reposición de recargo omitida: origen={}, corrección={}, la factura no admite recargo o no quedan días cobrables", origin.getId(), correction.getId());
            }
            return Optional.empty();
        }
        PolicyEvaluationResult evaluation = issuingPolicies.evaluateRulesUntilFirstViolation(context.get());
        if (!evaluation.allowed()) {
            if (logOmission) {
                log.warn("Reposición de recargo omitida: origen={}, corrección={}, codigos={}", origin.getId(), correction.getId(), evaluation.describeDenials());
            }
            return Optional.empty();
        }
        return context;
    }

    private Optional<Long> reinstateCorrection(Invoice origin, LateFeeChargeCorrection correction, LocalDate businessDate, LatePaymentTimeline timeline) {
        Optional<LateFeeIssuingContext> context = findIssuableContext(origin, origin.getPaymentStatus(), correction, businessDate, timeline, true);
        if (context.isEmpty()) {
            return Optional.empty();
        }
        Long debitNoteId = pipeline.runOperation(operation, context.get()).resultOrThrowIfAwaitingAuthorization(requestId -> new IllegalStateException(
                "Late fee reinstatement does not wait for authorization: " + requestId)).debitNote().getId();
        log.info("Recargo repuesto por la reversión del pago: origen={}, corrección={}, nota de débito={}, dias={}, monto={}", origin.getId(), correction.getId(), debitNoteId,
                context.get().period().chargedDays(), context.get().lateFeeAmount().value().amount());
        return Optional.of(debitNoteId);
    }
}
