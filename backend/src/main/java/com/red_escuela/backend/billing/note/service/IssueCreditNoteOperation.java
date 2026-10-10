package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoicePaymentEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineRequest;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.IssueCreditNoteCommand;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.policy.CreditNoteIssuingContext;
import com.red_escuela.backend.billing.note.policy.CreditNoteIssuingRejections;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
public class IssueCreditNoteOperation implements BillingOperation<IssueCreditNoteCommand, CreditNote> {
    private static final Logger log = LoggerFactory.getLogger(IssueCreditNoteOperation.class);

    private final InvoiceCreditAccessService invoices;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteNumberGenerator numberGenerator;
    private final PolicySet<CreditNoteIssuingContext> issuingPolicies;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public IssueCreditNoteOperation(InvoiceCreditAccessService invoices, StudentCurrentAccountService accountService,
                                    CurrentAccountAllocationService allocationService, CreditNoteRepository creditNoteRepository,
                                    CreditNoteNumberGenerator numberGenerator, PolicySet<CreditNoteIssuingContext> issuingPolicies,
                                    ApplicationEventPublisher eventPublisher, Clock clock) {
        this.invoices = invoices;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.creditNoteRepository = creditNoteRepository;
        this.numberGenerator = numberGenerator;
        this.issuingPolicies = issuingPolicies;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ISSUE_CREDIT_NOTE;
    }

    @Override
    public Class<IssueCreditNoteCommand> contextType() {
        return IssueCreditNoteCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(issuingPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(CreditNoteIssuedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(IssueCreditNoteCommand command) {
        Invoice invoice = invoices.findInvoice(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        requireAllowed(command, invoice);
    }

    @Override
    public CreditNote processOperation(IssueCreditNoteCommand command) {
        Long accountId = invoices.findAccountIdOf(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Invoice invoice = invoices.lockInvoice(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        requireAllowed(command, invoice);
        LocalDate businessDate = LocalDate.now(clock);
        StatusChangeActor actor = new StatusChangeActor(command.requestedByPersonId(), null);
        CreditNote note = CreditNote.issueCreditNoteForInvoice(invoice, command.reason(), command.justification(), toLineRequests(command, invoice),
                numberGenerator.nextCreditNoteNumber(), businessDate, businessDate, command.authorizationRequestId(), actor);
        invoice.applyCreditNote(note.toInvoiceCreditNoteApplication(invoice.getAmounts().outstandingAmount()), businessDate, actor);
        creditNoteRepository.save(note);
        accountService.recordCreditNoteIssuedCredit(account, note.getId(), note.getOffsetAmount(), note.getFreeCreditAmount(), businessDate,
                "Nota de crédito " + note.getCreditNoteNumber().value() + " sobre la factura " + note.getInvoiceNumber());
        publishInvoiceSettledByCreditNote(invoice, account, businessDate);
        if (!note.getFreeCreditAmount().value().isZero()) {
            allocationService.allocateAccountCreditToOldestInvoices(account, businessDate, actor);
        }
        log.info("Nota de crédito emitida: numero={}, factura={}, motivo={}, offset={}, libre={}, authorizationRequestId={}", note.getCreditNoteNumber().value(),
                note.getInvoiceNumber(), note.getReason(), note.getOffsetAmount().value().amount(), note.getFreeCreditAmount().value().amount(),
                note.getAuthorizationRequestId());
        return note;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(IssueCreditNoteCommand command, CreditNote note) {
        Long studentPersonId = invoices.findAccountOwnerPersonId(note.getInvoiceId()).orElseThrow();
        return List.of(CreditNoteIssuedEvent.issuedFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(),
                studentPersonId, command.requestedByPersonId(), note.getReason().name(), note.getTotalAmount().value().amount(),
                note.getCurrency().name(), note.getIssueDate()));
    }

    private void requireAllowed(IssueCreditNoteCommand command, Invoice invoice) {
        String creditNoteRecipient = invoices.findAccountOwnerDocumentNumber(invoice.getStudentCurrentAccountId()).orElse(null);
        issuingPolicies.evaluateRulesUntilFirstViolation(new CreditNoteIssuingContext(command, CreditedInvoiceFacts.of(invoice), creditNoteRecipient))
                .throwIfDenied(CreditNoteIssuingRejections::rejectionOf);
    }

    private List<CreditNoteLineRequest> toLineRequests(IssueCreditNoteCommand command, Invoice invoice) {
        return command.lines().stream()
                .map(line -> new CreditNoteLineRequest(line.lineNumber(), NonNegativeMoney.of(line.amount().amount(), invoice.getCurrency())))
                .toList();
    }

    private void publishInvoiceSettledByCreditNote(Invoice invoice, StudentCurrentAccount account, LocalDate businessDate) {
        boolean settled = invoice.pullRecordedDomainEvents().stream().anyMatch(InvoicePaidEvent.class::isInstance);
        if (settled) {
            eventPublisher.publishEvent(InvoicePaidDomainEvent.paidFor(invoice.getId(), account.getId(),
                    InvoicePaymentEvent.DEBT_REDUCTION_CREDIT_NOTE_SETTLED_INVOICE.name(), businessDate));
        }
    }
}
