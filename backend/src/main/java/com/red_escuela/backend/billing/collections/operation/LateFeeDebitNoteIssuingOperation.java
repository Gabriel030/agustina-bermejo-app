package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.domain.LateFeeAccrualPeriod;
import com.red_escuela.backend.billing.collections.domain.LateFeeCharge;
import com.red_escuela.backend.billing.collections.exception.LateFeeIssuingRejectedException;
import com.red_escuela.backend.billing.collections.policy.LateFeeIssuingContext;
import com.red_escuela.backend.billing.collections.policy.LateFeeOriginFacts;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeRepository;
import com.red_escuela.backend.billing.events.LateFeeIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.ChargeKey;
import com.red_escuela.backend.billing.invoice.domain.FeeType;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDraftHeader;
import com.red_escuela.backend.billing.invoice.domain.InvoiceIssuingReason;
import com.red_escuela.backend.billing.invoice.domain.InvoiceLineDraft;
import com.red_escuela.backend.billing.invoice.domain.InvoiceType;
import com.red_escuela.backend.billing.invoice.operation.DebitNoteIssuer;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class LateFeeDebitNoteIssuingOperation implements BillingOperation<LateFeeIssuingContext, LateFeeDebitNoteIssued> {
    private static final Logger log = LoggerFactory.getLogger(LateFeeDebitNoteIssuingOperation.class);
    private static final StatusChangeActor ACTOR = new StatusChangeActor(null, "LateFeeAccrual");
    private static final int MAX_CONCEPT_LENGTH = 200;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final PolicySet<LateFeeIssuingContext> issuingPolicies;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final DebitNoteIssuer debitNoteIssuer;
    private final InvoiceCreditAccessService invoiceAccess;
    private final LateFeeChargeRepository chargeRepository;
    private final MeterRegistry meterRegistry;

    public LateFeeDebitNoteIssuingOperation(PolicySet<LateFeeIssuingContext> debitNoteIssuingPolicySet, StudentCurrentAccountService accountService,
                                            CurrentAccountAllocationService allocationService, DebitNoteIssuer debitNoteIssuer,
                                            InvoiceCreditAccessService invoiceAccess, LateFeeChargeRepository chargeRepository, MeterRegistry meterRegistry) {
        this.issuingPolicies = debitNoteIssuingPolicySet;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.debitNoteIssuer = debitNoteIssuer;
        this.invoiceAccess = invoiceAccess;
        this.chargeRepository = chargeRepository;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_LATE_FEE_DEBIT_NOTE;
    }

    @Override
    public Class<LateFeeIssuingContext> contextType() {
        return LateFeeIssuingContext.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(issuingPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(LateFeeIssuedEvent.class);
    }

    @Override
    public void requireValidContext(LateFeeIssuingContext context) {
        issuingPolicies.evaluateRulesUntilFirstViolation(context).throwIfDenied(LateFeeIssuingRejectedException::new);
    }

    @Override
    public LateFeeDebitNoteIssued processOperation(LateFeeIssuingContext context) {
        LateFeeOriginFacts origin = context.origin();
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(context.draft().studentCurrentAccountId());
        ChargeKey chargeKey = ChargeKey.forLateFee(origin.invoiceId(), context.period().period().firstDay());
        InvoiceDraftHeader header = new InvoiceDraftHeader(account.getId(), context.draft().currency(), InvoiceDocumentType.DEBIT_NOTE, InvoiceType.LATE_FEE,
                InvoiceIssuingReason.LATE_FEE, origin.invoiceId());
        InvoiceLineDraft line = new InvoiceLineDraft(FeeType.LATE_FEE, conceptOf(context), origin.period(), origin.programEnrollmentId(),
                origin.courseEnrollmentId(), null, null, null, chargeKey.value(), context.lateFeeAmount());
        Invoice issued = debitNoteIssuer.issueLateFeeDebitNote(header, line, context.issueDate(), context.debitNoteDueDate(), context.draft().recipient(), ACTOR);
        accountService.recordDebitNoteIssuedDebit(account, issued, context.issueDate());
        LateFeeCharge charge = chargeRepository.save(LateFeeCharge.recordLateFeeCharge(issued.getId(), origin.invoiceId(), account.getId(), context.period(),
                context.dueDateVersionId(), context.dueDateTerms().dailyLateFeeRate(), origin.finalLinePrice(), context.settlementSource()));
        allocationService.allocateAccountCreditToOldestInvoices(account, context.issueDate(), ACTOR);
        recordIssuance(context, issued);
        return new LateFeeDebitNoteIssued(issued, charge);
    }

    @Override
    public List<DomainEvent> domainEventsOf(LateFeeIssuingContext context, LateFeeDebitNoteIssued result) {
        Long studentPersonId = invoiceAccess.findAccountOwnerPersonId(context.origin().invoiceId()).orElseThrow();
        return List.of(LateFeeIssuedEvent.issuedFor(result.debitNote().getId(), result.debitNote().getInvoiceNumber().value(), context.origin().invoiceId(),
                context.origin().invoiceNumber(), studentPersonId, context.period().period().firstDay(), context.period().period().lastDay(),
                context.period().chargedDays(), context.period().kind(), context.issueDate(), context.lateFeeAmount().value()));
    }

    private void recordIssuance(LateFeeIssuingContext context, Invoice issued) {
        LateFeeAccrualPeriod period = context.period().period();
        long daysLate = ChronoUnit.DAYS.between(period.lastDay(), context.issueDate());
        log.info("Recargo emitido: nota={}, origen={}, periodo={}..{}, diasCobrados={}, diasDelPeriodo={}, tasaDiaria={}, base={}, monto={}, corte={}, fuenteDelSaldado={}, versionMora={}, diasDeDemora={}",
                issued.getInvoiceNumber().value(), context.origin().invoiceNumber(), period.firstDay(), period.lastDay(), context.period().chargedDays(), period.days(),
                context.dueDateTerms().dailyLateFeeRate().fraction(), context.origin().finalLinePrice().value().amount(), context.lateFeeAmount().value().amount(),
                context.period().kind(), context.settlementSource().orElse(null), context.dueDateVersionId(), daysLate);
        if (daysLate > LateFeeIssuedEvent.ISSUING_WINDOW_DAYS) {
            log.warn("Recargo emitido fuera del plazo de {} días de la RG 4540: nota={}, origen={}, periodo={}..{}, fechaHecho={}, emitido={}",
                    LateFeeIssuedEvent.ISSUING_WINDOW_DAYS, issued.getInvoiceNumber().value(), context.origin().invoiceNumber(), period.firstDay(), period.lastDay(),
                    period.lastDay(), context.issueDate());
        }
        String currency = context.draft().currency().name();
        String cutoffKind = context.period().kind().name();
        meterRegistry.counter("billing_late_fees_issued_total", "currency", currency, "cutoff_kind", cutoffKind).increment();
        meterRegistry.counter("billing_late_fees_amount_total", "currency", currency).increment(context.lateFeeAmount().value().amount().doubleValue());
        meterRegistry.counter("billing_late_fees_charged_days_total", "currency", currency).increment(context.period().chargedDays());
    }

    private String conceptOf(LateFeeIssuingContext context) {
        LateFeeAccrualPeriod period = context.period().period();
        String concept = "Recargo por mora " + context.origin().invoiceNumber() + " del " + DAY_MONTH.format(period.firstDay()) + " al " + DAY_MONTH.format(period.lastDay())
                + " (" + context.period().chargedDays() + " días)";
        return concept.length() > MAX_CONCEPT_LENGTH ? concept.substring(0, MAX_CONCEPT_LENGTH) : concept;
    }
}
