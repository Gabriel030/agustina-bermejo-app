package com.red_escuela.backend.billing.collections.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.collections.domain.LateFeeChargeCorrection;
import com.red_escuela.backend.billing.collections.domain.LateFeeCorrectionCause;
import com.red_escuela.backend.billing.collections.domain.LateFeeCorrectionFiscalWindow;
import com.red_escuela.backend.billing.collections.exception.LateFeeCorrectionRejectedException;
import com.red_escuela.backend.billing.collections.policy.LateFeeCorrectionContext;
import com.red_escuela.backend.billing.collections.repository.LateFeeChargeCorrectionRepository;
import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.billing.events.LateFeeAnnulledByPaymentDateEvent;
import com.red_escuela.backend.billing.events.LateFeeCorrectionIssuedAfterFiscalWindowEvent;
import com.red_escuela.backend.billing.exception.InvoiceNotFoundException;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.LateFeeCorrectionCreditNoteKind;
import com.red_escuela.backend.billing.note.service.LateFeeCorrectionCreditNoteIssuer;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.event.DomainEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class LateFeeCorrectionCreditNoteOperation implements BillingOperation<IssueLateFeeCorrectionCommand, Optional<LateFeeCorrectionIssued>> {
    private static final Logger log = LoggerFactory.getLogger(LateFeeCorrectionCreditNoteOperation.class);
    private static final DateTimeFormatter DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PolicySet<LateFeeCorrectionContext> correctionPolicies;
    private final InvoiceCreditAccessService invoiceAccess;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final LateFeeCorrectionCreditNoteIssuer creditNoteIssuer;
    private final LateFeeChargeCorrectionRepository correctionRepository;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public LateFeeCorrectionCreditNoteOperation(PolicySet<LateFeeCorrectionContext> lateFeeCorrectionPolicySet, InvoiceCreditAccessService invoiceAccess,
                                                StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                                LateFeeCorrectionCreditNoteIssuer creditNoteIssuer,
                                                LateFeeChargeCorrectionRepository correctionRepository, MeterRegistry meterRegistry, Clock clock) {
        this.correctionPolicies = lateFeeCorrectionPolicySet;
        this.invoiceAccess = invoiceAccess;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.creditNoteIssuer = creditNoteIssuer;
        this.correctionRepository = correctionRepository;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_LATE_FEE_CORRECTION_CREDIT_NOTE;
    }

    @Override
    public Class<IssueLateFeeCorrectionCommand> contextType() {
        return IssueLateFeeCorrectionCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(correctionPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(CreditNoteIssuedEvent.class, LateFeeAnnulledByPaymentDateEvent.class, LateFeeCorrectionIssuedAfterFiscalWindowEvent.class);
    }

    @Override
    public void requireValidContext(IssueLateFeeCorrectionCommand command) {
        requireAllowed(findDebitNote(command));
    }

    @Override
    public Optional<LateFeeCorrectionIssued> processOperation(IssueLateFeeCorrectionCommand command) {
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(command.studentCurrentAccountId());
        Invoice debitNote = invoiceAccess.lockInvoice(command.debitNoteId()).orElseThrow(() -> new InvoiceNotFoundException(command.debitNoteId()));
        requireAllowed(debitNote);
        NonNegativeMoney planned = command.correction().creditAmount();
        NonNegativeMoney creditable = debitNote.getPrimaryLine().calculateCreditableAmount();
        NonNegativeMoney credit = planned.value().isGreaterThan(creditable.value()) ? creditable : planned;
        if (credit.value().isZero()) {
            log.warn("Corrección de recargo sin monto acreditable: nota de débito={}, planificado={}", debitNote.getInvoiceNumber().value(), planned.value().amount());
            meterRegistry.counter("billing_late_fee_corrections_capped_total", "currency", debitNote.getCurrency().name()).increment();
            return Optional.empty();
        }
        if (planned.value().isGreaterThan(credit.value())) {
            log.warn("Corrección de recargo acreditada solo hasta lo acreditable: nota de débito={}, planificado={}, acreditado={}",
                    debitNote.getInvoiceNumber().value(), planned.value().amount(), credit.value().amount());
            meterRegistry.counter("billing_late_fee_corrections_capped_total", "currency", debitNote.getCurrency().name()).increment();
        }
        LateFeeCorrectionCreditNoteKind kind = kindOf(command.cause());
        CreditNote note = creditNoteIssuer.issueCorrectionCreditNote(debitNote, credit, justificationOf(command, debitNote), command.factDate(), kind);
        LocalDate businessDate = LocalDate.now(clock);
        accountService.recordCreditNoteIssuedCredit(account, note.getId(), note.getOffsetAmount(), note.getFreeCreditAmount(), businessDate,
                ledgerDescriptionOf(command.cause()) + note.getInvoiceNumber() + " (nota de crédito " + note.getCreditNoteNumber().value() + ")");
        allocationService.allocateAccountCreditToOldestInvoices(account, businessDate, kind.actor());
        LateFeeChargeCorrection correction = correctionRepository.save(correctionRecordOf(command, debitNote, account, note));
        recordCorrection(note, correction, debitNote);
        return Optional.of(new LateFeeCorrectionIssued(note, correction));
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(IssueLateFeeCorrectionCommand command, Optional<LateFeeCorrectionIssued> result) {
        return result.map(issued -> {
            CreditNote note = issued.creditNote();
            Long studentPersonId = invoiceAccess.findAccountOwnerPersonId(note.getInvoiceId()).orElseThrow();
            List<DomainEvent> events = new ArrayList<>();
            if (command.cause().isPaymentDate()) {
                events.add(LateFeeAnnulledByPaymentDateEvent.annulledFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(),
                        studentPersonId, issued.correction().getCorrectedDays(), command.referenceDate(), command.cause() == LateFeeCorrectionCause.PAYMENT_WITHIN_GRACE,
                        note.getTotalAmount()));
            } else {
                events.add(CreditNoteIssuedEvent.issuedFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(),
                        studentPersonId, null, note.getReason().name(), note.getTotalAmount().value().amount(), note.getCurrency().name(), note.getIssueDate()));
            }
            if (LateFeeCorrectionFiscalWindow.isOutside(command.factDate(), note.getIssueDate())) {
                events.add(LateFeeCorrectionIssuedAfterFiscalWindowEvent.issuedFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(),
                        note.getInvoiceNumber(), command.cause().name(), command.factDate(), note.getIssueDate()));
            }
            return events;
        }).orElse(List.of());
    }

    private Invoice findDebitNote(IssueLateFeeCorrectionCommand command) {
        return invoiceAccess.findInvoice(command.debitNoteId()).orElseThrow(() -> new InvoiceNotFoundException(command.debitNoteId()));
    }

    private void requireAllowed(Invoice debitNote) {
        correctionPolicies.evaluateRulesUntilFirstViolation(new LateFeeCorrectionContext(debitNote.getId(), debitNote.getDocumentType(), debitNote.getInvoiceType(),
                debitNote.getDocumentStatus(), debitNote.getPaymentStatus())).throwIfDenied(LateFeeCorrectionRejectedException::new);
    }

    private static LateFeeCorrectionCreditNoteKind kindOf(LateFeeCorrectionCause cause) {
        return cause.isPaymentDate() ? LateFeeCorrectionCreditNoteKind.AFTER_PAYMENT_DATE : LateFeeCorrectionCreditNoteKind.AFTER_WITHDRAWAL;
    }

    private static String ledgerDescriptionOf(LateFeeCorrectionCause cause) {
        return cause.isPaymentDate() ? "Anulación del recargo " : "Corrección del recargo ";
    }

    private static LateFeeChargeCorrection correctionRecordOf(IssueLateFeeCorrectionCommand command, Invoice debitNote, StudentCurrentAccount account, CreditNote note) {
        if (command.cause().isPaymentDate()) {
            return LateFeeChargeCorrection.recordPaymentDateCorrection(command.lateFeeChargeId(), debitNote.getId(), account.getId(), note.getId(), command.correction(),
                    command.settlementAllocationId(), command.referenceDate(), command.cause());
        }
        return LateFeeChargeCorrection.recordBackdatedWithdrawalCorrection(command.lateFeeChargeId(), debitNote.getId(), account.getId(), note.getId(), command.correction(),
                command.referenceDate());
    }

    private String justificationOf(IssueLateFeeCorrectionCommand command, Invoice debitNote) {
        String invoiceNumber = debitNote.getInvoiceNumber().value();
        String referenceDay = DAY_MONTH_YEAR.format(command.referenceDate());
        return switch (command.cause()) {
            case BACKDATED_WITHDRAWAL -> "Corrección del recargo por mora: " + command.correction().correctedDays() + " días posteriores a la baja informada del "
                    + referenceDay + " (" + invoiceNumber + ")";
            case PAYMENT_DATED_BEFORE_CHARGED_DAYS -> "Anulación del recargo por mora: " + command.correction().correctedDays() + " días posteriores al pago del "
                    + referenceDay + " (" + invoiceNumber + ")";
            case PAYMENT_WITHIN_GRACE -> "Anulación del recargo por mora: pago del " + referenceDay + " dentro de la gracia (" + invoiceNumber + ")";
        };
    }

    private void recordCorrection(CreditNote note, LateFeeChargeCorrection correction, Invoice debitNote) {
        log.info("Recargo corregido: causa={}, notaDeDébito={}, notaDeCrédito={}, dias={}, desde={}, hasta={}, fechaDeReferencia={}, monto={}", correction.getCause(),
                debitNote.getInvoiceNumber().value(), note.getCreditNoteNumber().value(), correction.getCorrectedDays(), correction.getFirstCorrectedDay(),
                correction.getLastCorrectedDay(), correction.getCause().isPaymentDate() ? correction.getPaymentDate() : correction.getEnrollmentEndDate(),
                note.getTotalAmount().value().amount());
        String currency = note.getCurrency().name();
        if (correction.getCause().isPaymentDate()) {
            meterRegistry.counter("billing_late_fees_overcharged_days_total").increment(correction.getCorrectedDays());
        }
        meterRegistry.counter("billing_late_fee_corrections_total", "currency", currency, "cause", correction.getCause().name()).increment();
        meterRegistry.counter("billing_late_fee_corrections_amount_total", "currency", currency).increment(note.getTotalAmount().value().amount().doubleValue());
        meterRegistry.counter("billing_late_fee_corrections_days_total", "currency", currency).increment(correction.getCorrectedDays());
    }
}
