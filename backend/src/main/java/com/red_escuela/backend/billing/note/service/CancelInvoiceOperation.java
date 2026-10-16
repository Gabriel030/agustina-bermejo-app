package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceCancelledEvent;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationContext;
import com.red_escuela.backend.billing.note.policy.InvoiceCancellationRejections;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
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
public class CancelInvoiceOperation implements BillingOperation<CancelInvoiceCommand, CreditNote> {
    private static final Logger log = LoggerFactory.getLogger(CancelInvoiceOperation.class);

    private final InvoiceCreditAccessService invoices;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteNumberGenerator numberGenerator;
    private final PolicySet<InvoiceCancellationContext> cancellationPolicies;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public CancelInvoiceOperation(InvoiceCreditAccessService invoices, StudentCurrentAccountService accountService,
                                  CurrentAccountAllocationService allocationService, CreditNoteRepository creditNoteRepository,
                                  CreditNoteNumberGenerator numberGenerator, PolicySet<InvoiceCancellationContext> cancellationPolicies,
                                  ApplicationEventPublisher eventPublisher, Clock clock) {
        this.invoices = invoices;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.creditNoteRepository = creditNoteRepository;
        this.numberGenerator = numberGenerator;
        this.cancellationPolicies = cancellationPolicies;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_INVOICE;
    }

    @Override
    public Class<CancelInvoiceCommand> contextType() {
        return CancelInvoiceCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(cancellationPolicies.moment());
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(CreditNoteIssuedEvent.class);
    }

    @Override
    public void requireValidContext(CancelInvoiceCommand command) {
        Invoice invoice = invoices.findInvoice(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        requireAllowed(command, invoice);
    }

    @Override
    public CreditNote processOperation(CancelInvoiceCommand command) {
        CreditNote note = cancelWithoutAllocatingCredit(command);
        allocateAccountCreditOf(note.getInvoiceId(), command.actor());
        return note;
    }

    public void allocateAccountCreditOf(Long invoiceId, StatusChangeActor actor) {
        Long accountId = invoices.findAccountIdOf(invoiceId).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(invoiceId));
        allocationService.allocateAccountCreditToOldestInvoices(accountService.lockStudentCurrentAccount(accountId), LocalDate.now(clock), actor);
    }

    public CreditNote cancelWithoutAllocatingCredit(CancelInvoiceCommand command) {
        Long accountId = invoices.findAccountIdOf(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Invoice invoice = invoices.lockInvoice(command.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(command.invoiceId()));
        requireAllowed(command, invoice);
        LocalDate businessDate = LocalDate.now(clock);
        StatusChangeActor actor = command.actor();
        CreditNote note = CreditNote.issueCancellationCreditNoteForInvoice(invoice, command.reason(), command.justification(), numberGenerator.nextCreditNoteNumber(),
                businessDate, businessDate, command.authorizationRequestId(), actor);
        invoice.applyCreditNote(note.toInvoiceCancellationApplication(command.releasesChargeKeys()), businessDate, actor);
        creditNoteRepository.save(note);
        accountService.recordCreditNoteIssuedCredit(account, note.getId(), note.getOffsetAmount(), note.getFreeCreditAmount(), businessDate,
                "Cancelación de la factura " + note.getInvoiceNumber() + " (nota de crédito " + note.getCreditNoteNumber().value() + ")");
        invoice.pullRecordedDomainEvents().stream().filter(InvoiceCancelledEvent.class::isInstance).forEach(eventPublisher::publishEvent);
        log.info("Factura cancelada: numero={}, notaDeCredito={}, motivo={}, claveLiberada={}, origenDeSistema={}, authorizationRequestId={}", note.getInvoiceNumber(),
                note.getCreditNoteNumber().value(), note.getReason(), command.releasesChargeKeys(), command.systemOrigin(), note.getAuthorizationRequestId());
        return note;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CancelInvoiceCommand command, CreditNote note) {
        Long studentPersonId = invoices.findAccountOwnerPersonId(note.getInvoiceId()).orElseThrow();
        return List.of(CreditNoteIssuedEvent.issuedFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(),
                studentPersonId, command.requestedByPersonId(), note.getReason().name(), note.getTotalAmount().value().amount(),
                note.getCurrency().name(), note.getIssueDate()));
    }

    private void requireAllowed(CancelInvoiceCommand command, Invoice invoice) {
        cancellationPolicies.evaluateRulesUntilFirstViolation(new InvoiceCancellationContext(command, CreditedInvoiceFacts.of(invoice)))
                .throwIfDenied(violations -> InvoiceCancellationRejections.rejectionOf(violations, invoice.getPaymentStatus()));
    }
}
