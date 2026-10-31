package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.domain.event.InvoiceIssuedDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.invoice.domain.issuing.DraftInvoiceApprovalCommand;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.DraftInvoiceDueDateResolver;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.invoice.service.StudentSnapshotFinder;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
public class DraftInvoiceApprovalOperation implements BillingOperation<DraftInvoiceApprovalCommand, List<Invoice>> {
    private static final Logger log = LoggerFactory.getLogger(DraftInvoiceApprovalOperation.class);

    private final InvoiceRepository invoiceRepository;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceNumberGenerator numberGenerator;
    private final DraftInvoiceDueDateResolver dueDateResolver;
    private final StudentSnapshotFinder snapshotFinder;

    public DraftInvoiceApprovalOperation(InvoiceRepository invoiceRepository, StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                         InvoiceNumberGenerator numberGenerator, DraftInvoiceDueDateResolver dueDateResolver, StudentSnapshotFinder snapshotFinder) {
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.numberGenerator = numberGenerator;
        this.dueDateResolver = dueDateResolver;
        this.snapshotFinder = snapshotFinder;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.APPROVE_DRAFT_INVOICES;
    }

    @Override
    public Class<DraftInvoiceApprovalCommand> contextType() {
        return DraftInvoiceApprovalCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of();
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(InvoiceIssuedDomainEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(DraftInvoiceApprovalCommand command) {
    }

    @Override
    public List<Invoice> processOperation(DraftInvoiceApprovalCommand command) {
        TreeSet<Long> orderedIds = new TreeSet<>(command.invoiceIds());
        Map<Long, StudentCurrentAccount> lockedAccounts = lockAccountsOfDrafts(orderedIds);
        List<Invoice> drafts = DraftInvoiceLocking.lockPendingDraftsOfRun(invoiceRepository, command.monthlyInvoiceRunId(), orderedIds);
        drafts.forEach(draft -> approveDraft(draft, lockedAccounts.get(draft.getStudentCurrentAccountId()), command));
        log.info("Borradores aprobados: corrida={}, cantidad={}, fecha={}", command.monthlyInvoiceRunId(), drafts.size(), command.approvalDate());
        return drafts;
    }

    @Override
    public List<DomainEvent> domainEventsOf(DraftInvoiceApprovalCommand command, List<Invoice> approved) {
        return approved.stream().flatMap(draft -> RecordedInvoiceEventTranslator.translateRecordedEventsOf(draft).stream()).toList();
    }

    private Map<Long, StudentCurrentAccount> lockAccountsOfDrafts(TreeSet<Long> orderedIds) {
        TreeSet<Long> accountIds = new TreeSet<>(invoiceRepository.findAccountIdsOfInvoices(orderedIds));
        Map<Long, StudentCurrentAccount> locked = new HashMap<>();
        accountIds.forEach(accountId -> locked.put(accountId, accountService.lockStudentCurrentAccount(accountId)));
        return locked;
    }

    private void approveDraft(Invoice draft, StudentCurrentAccount account, DraftInvoiceApprovalCommand command) {
        LocalDate approvalDate = command.approvalDate();
        draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), approvalDate, dueDateResolver.resolveDueDateOfDraftApprovedOn(draft, approvalDate),
                snapshotFinder.findSnapshotOfStudent(account.getStudentId()), command.actor());
        invoiceRepository.flush();
        accountService.recordInvoiceIssuedDebit(account, draft, approvalDate);
        allocationService.allocateAccountCreditToOldestInvoices(account, approvalDate, command.actor());
    }
}
