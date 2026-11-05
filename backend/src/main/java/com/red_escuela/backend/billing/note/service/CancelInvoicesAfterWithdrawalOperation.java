package com.red_escuela.backend.billing.note.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.events.CreditNoteIssuedEvent;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.service.InvoiceCreditAccessService;
import com.red_escuela.backend.billing.note.domain.CancelInvoiceCommand;
import com.red_escuela.backend.billing.note.domain.CancelInvoicesAfterWithdrawalCommand;
import com.red_escuela.backend.billing.note.domain.CreditNote;
import com.red_escuela.backend.billing.note.domain.CreditNoteLineRequest;
import com.red_escuela.backend.billing.note.domain.CreditNoteReason;
import com.red_escuela.backend.billing.note.domain.CreditedInvoiceFacts;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationItem;
import com.red_escuela.backend.billing.note.domain.WithdrawalCancellationKind;
import com.red_escuela.backend.billing.note.exception.CreditNoteInvoiceNotFoundException;
import com.red_escuela.backend.billing.note.exception.WithdrawalReductionRejectedException;
import com.red_escuela.backend.billing.note.policy.WithdrawalLineReductionContext;
import com.red_escuela.backend.billing.note.repository.CreditNoteRepository;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.domain.NonNegativeMoney;
import com.red_escuela.backend.shared.domain.policy.PolicySet;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class CancelInvoicesAfterWithdrawalOperation implements BillingOperation<CancelInvoicesAfterWithdrawalCommand, List<CreditNote>> {
    private static final Logger log = LoggerFactory.getLogger(CancelInvoicesAfterWithdrawalOperation.class);

    private final InvoiceCreditAccessService invoices;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final CreditNoteRepository creditNoteRepository;
    private final CreditNoteNumberGenerator numberGenerator;
    private final PolicySet<WithdrawalLineReductionContext> reductionPolicies;
    private final CancelInvoiceOperation cancelInvoiceOperation;
    private final Clock clock;

    public CancelInvoicesAfterWithdrawalOperation(InvoiceCreditAccessService invoices, StudentCurrentAccountService accountService,
                                                  CurrentAccountAllocationService allocationService, CreditNoteRepository creditNoteRepository,
                                                  CreditNoteNumberGenerator numberGenerator, PolicySet<WithdrawalLineReductionContext> reductionPolicies,
                                                  CancelInvoiceOperation cancelInvoiceOperation, Clock clock) {
        this.invoices = invoices;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.creditNoteRepository = creditNoteRepository;
        this.numberGenerator = numberGenerator;
        this.reductionPolicies = reductionPolicies;
        this.cancelInvoiceOperation = cancelInvoiceOperation;
        this.clock = clock;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.CANCEL_INVOICES_AFTER_WITHDRAWAL;
    }

    @Override
    public Class<CancelInvoicesAfterWithdrawalCommand> contextType() {
        return CancelInvoicesAfterWithdrawalCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of(reductionPolicies.moment(), cancelInvoiceOperation.validationMoments().get(0));
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(CreditNoteIssuedEvent.class);
    }

    @Override
    public void requireValidContext(CancelInvoicesAfterWithdrawalCommand command) {
        for (WithdrawalCancellationItem item : command.items()) {
            if (item.kind() == WithdrawalCancellationKind.CANCEL_INVOICE) {
                cancelInvoiceOperation.requireValidContext(cancellationOf(command, item));
            } else {
                Invoice invoice = invoices.findInvoice(item.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(item.invoiceId()));
                requireReducible(item, invoice);
            }
        }
    }

    @Override
    public List<CreditNote> processOperation(CancelInvoicesAfterWithdrawalCommand command) {
        List<CreditNote> notes = new ArrayList<>();
        Set<Long> accountIds = new LinkedHashSet<>();
        for (WithdrawalCancellationItem item : command.items()) {
            accountIds.add(invoices.findAccountIdOf(item.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(item.invoiceId())));
            notes.add(item.kind() == WithdrawalCancellationKind.CANCEL_INVOICE
                    ? cancelInvoiceOperation.cancelWithoutAllocatingCredit(cancellationOf(command, item)) : reduceInvoiceLines(command, item));
        }
        StatusChangeActor actor = StatusChangeActor.ofPerson(command.requestedByPersonId());
        accountIds.stream().sorted().forEach(accountId -> allocationService.allocateAccountCreditToOldestInvoices(accountService.lockStudentCurrentAccount(accountId),
                LocalDate.now(clock), actor));
        log.info("Cancelación de períodos posteriores a la baja: facturas={}, notasDeCredito={}, authorizationRequestId={}", command.items().size(), notes.size(),
                command.authorizationRequestId());
        return notes;
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(CancelInvoicesAfterWithdrawalCommand command, List<CreditNote> notes) {
        return notes.stream().map(note -> {
            Long studentPersonId = invoices.findAccountOwnerPersonId(note.getInvoiceId()).orElseThrow();
            return CreditNoteIssuedEvent.issuedFor(note.getId(), note.getCreditNoteNumber().value(), note.getInvoiceId(), note.getInvoiceNumber(), studentPersonId,
                    command.requestedByPersonId(), note.getReason().name(), note.getTotalAmount().value().amount(), note.getCurrency().name(), note.getIssueDate());
        }).toList();
    }

    private CancelInvoiceCommand cancellationOf(CancelInvoicesAfterWithdrawalCommand command, WithdrawalCancellationItem item) {
        return new CancelInvoiceCommand(item.invoiceId(), CreditNoteReason.CANCELLATION, command.justification(), command.requestedByPersonId(),
                command.requestedByDirector(), null, command.authorizationRequestId());
    }

    private CreditNote reduceInvoiceLines(CancelInvoicesAfterWithdrawalCommand command, WithdrawalCancellationItem item) {
        Long accountId = invoices.findAccountIdOf(item.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(item.invoiceId()));
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        Invoice invoice = invoices.lockInvoice(item.invoiceId()).orElseThrow(() -> new CreditNoteInvoiceNotFoundException(item.invoiceId()));
        requireReducible(item, invoice);
        LocalDate businessDate = LocalDate.now(clock);
        StatusChangeActor actor = StatusChangeActor.ofPerson(command.requestedByPersonId());
        List<CreditNoteLineRequest> lineRequests = item.lines().stream()
                .map(line -> new CreditNoteLineRequest(line.lineNumber(), NonNegativeMoney.of(line.amount()))).toList();
        CreditNote note = CreditNote.issueCreditNoteForInvoice(invoice, CreditNoteReason.CANCELLATION, command.justification(), lineRequests,
                numberGenerator.nextCreditNoteNumber(), businessDate, businessDate, command.authorizationRequestId(), actor);
        invoice.applyCreditNote(note.toInvoiceCreditNoteApplication(invoice.getAmounts().outstandingAmount()), businessDate, actor);
        creditNoteRepository.save(note);
        accountService.recordCreditNoteIssuedCredit(account, note.getId(), note.getOffsetAmount(), note.getFreeCreditAmount(), businessDate,
                "Baja: reducción de períodos posteriores de la factura " + note.getInvoiceNumber() + " (nota de crédito " + note.getCreditNoteNumber().value() + ")");
        return note;
    }

    private void requireReducible(WithdrawalCancellationItem item, Invoice invoice) {
        reductionPolicies.evaluateRulesUntilFirstViolation(new WithdrawalLineReductionContext(item, CreditedInvoiceFacts.of(invoice)))
                .throwIfDenied(WithdrawalReductionRejectedException::new);
    }
}
