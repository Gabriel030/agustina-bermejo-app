package com.red_escuela.backend.billing.issuing;

import com.red_escuela.backend.billing.entity.Invoice;
import com.red_escuela.backend.billing.entity.InvoiceDraft;
import com.red_escuela.backend.billing.exception.DuplicateChargeException;
import com.red_escuela.backend.billing.exception.PriceRevisionNotFoundException;
import com.red_escuela.backend.billing.repository.InvoiceRepository;
import com.red_escuela.backend.enrollment.repository.CourseEnrollmentRepository;
import com.red_escuela.backend.enrollment.repository.ProgramEnrollmentRepository;
import com.red_escuela.backend.shared.domain.policy.PolicyEvaluation;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
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
import java.time.Clock;
import java.time.LocalDate;

@Service
public class ChargeIssuer {
    private static final Logger log = LoggerFactory.getLogger(ChargeIssuer.class);
    private static final String UNIQUE_VIOLATION = "23505";
    private final PolicySet<IssuanceContext> issuancePolicies;
    private final IssuanceContextLoader contextLoader;
    private final ChargeAdjustmentApplier adjustmentApplier;
    private final InvoiceRepository invoiceRepository;
    private final ProgramEnrollmentRepository programEnrollmentRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final TransactionTemplate ownTransaction;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public ChargeIssuer(PolicySet<IssuanceContext> issuancePolicies, IssuanceContextLoader contextLoader,
                        ChargeAdjustmentApplier adjustmentApplier, InvoiceRepository invoiceRepository,
                        ProgramEnrollmentRepository programEnrollmentRepository, CourseEnrollmentRepository courseEnrollmentRepository,
                        PlatformTransactionManager transactionManager, MeterRegistry meterRegistry, Clock clock) {
        this.issuancePolicies = issuancePolicies;
        this.contextLoader = contextLoader;
        this.adjustmentApplier = adjustmentApplier;
        this.invoiceRepository = invoiceRepository;
        this.programEnrollmentRepository = programEnrollmentRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    public IssueResult issueInOwnTransaction(ChargeRequest request) {
        try {
            return ownTransaction.execute(status -> doIssue(request));
        } catch (DataIntegrityViolationException e) {
            if (!isDuplicateViolation(e)) {
                throw e;
            }
            log.warn("Cargo duplicado detectado por la base: tipo={}, periodo={}, motivo={}", request.feeType(), request.period(), request.reason());
            return new IssueResult.AlreadyIssued(null);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public IssueResult issueInCurrentTransaction(ChargeRequest request) {
        try {
            return doIssue(request);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateViolation(e)) {
                throw new DuplicateChargeException(request.feeType() + " " + request.period());
            }
            throw e;
        }
    }

    private IssueResult doIssue(ChargeRequest request) {
        ChargeRequest locked = lockTarget(request);
        LocalDate today = LocalDate.now(clock);
        IssuanceContext context = contextLoader.load(locked, today);
        PolicyEvaluation evaluation = issuancePolicies.evaluateUntilFirstDenial(context);
        if (!evaluation.allowed()) {
            if (context.alreadyIssued() && evaluation.firstDenial().map(v -> "ALREADY_ISSUED".equals(v.code())).orElse(false)) {
                log.info("Cargo ya emitido: factura={}, tipo={}, periodo={}, motivo={}", context.existingInvoiceId(), request.feeType(), request.period(), request.reason());
                return new IssueResult.AlreadyIssued(context.existingInvoiceId());
            }
            log.warn("Cargo rechazado: tipo={}, periodo={}, motivo={}, codigos={}", request.feeType(), request.period(), request.reason(), evaluation.describeDenials());
            evaluation.denials().forEach(v -> meterRegistry.counter("billing_charge_rejected_total", "reason", request.reason().name(), "code", v.code()).increment());
            return new IssueResult.Rejected(evaluation);
        }
        if (context.amount() == null) {
            throw new PriceRevisionNotFoundException("No PriceRevision found for " + request.feeType() + " " + request.target().getClass().getSimpleName());
        }
        Invoice invoice = invoiceRepository.saveAndFlush(Invoice.issue(draftOf(context), today));
        adjustmentApplier.apply(invoice, context);
        log.info("Factura emitida: factura={}, tipo={}, periodo={}, vencimiento={}, motivo={}, priceRevision={}", invoice.getId(), invoice.getFeeType(),
                invoice.getPeriod(), invoice.getDueDate(), request.reason(), invoice.getPriceRevision() == null ? null : invoice.getPriceRevision().getId());
        meterRegistry.counter("billing_charge_issued_total", "reason", request.reason().name(), "feeType", request.feeType().name()).increment();
        return new IssueResult.Issued(invoice);
    }

    private InvoiceDraft draftOf(IssuanceContext context) {
        ChargeRequest r = context.request();
        var program = r.target() instanceof ChargeTarget.ProgramStudent t ? t.enrollment() : null;
        var course = r.target() instanceof ChargeTarget.CourseStudent t ? t.enrollment() : null;
        return new InvoiceDraft(program, course, r.selection(), r.feeType(), r.period(), r.dueDate(), context.amount(), r.reason(),
                context.priceRevision(), r.templateId(), r.slot(), r.description(), r.subject(), r.examBoardRegistration());
    }

    private ChargeRequest lockTarget(ChargeRequest request) {
        return switch (request.target()) {
            case ChargeTarget.ProgramStudent t -> withTarget(request, new ChargeTarget.ProgramStudent(
                    programEnrollmentRepository.findByIdForUpdate(t.enrollment().getId()).orElseThrow()));
            case ChargeTarget.CourseStudent t -> withTarget(request, new ChargeTarget.CourseStudent(
                    courseEnrollmentRepository.findByIdForUpdate(t.enrollment().getId()).orElseThrow()));
            default -> request;
        };
    }

    private ChargeRequest withTarget(ChargeRequest r, ChargeTarget target) {
        return new ChargeRequest(target, r.feeType(), r.period(), r.dueDate(), r.reason(), r.selection(), r.templateId(), r.slot(), r.entry(),
                r.explicitAmount(), r.description(), r.subject(), r.examBoardRegistration());
    }

    private static boolean isDuplicateViolation(DataIntegrityViolationException e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
