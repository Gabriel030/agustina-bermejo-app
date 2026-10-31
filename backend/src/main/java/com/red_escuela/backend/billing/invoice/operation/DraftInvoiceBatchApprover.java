package com.red_escuela.backend.billing.invoice.operation;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.domain.Invoice;
import com.red_escuela.backend.billing.invoice.domain.InvoiceDocumentType;
import com.red_escuela.backend.billing.invoice.repository.InvoiceRepository;
import com.red_escuela.backend.billing.invoice.service.DraftInvoiceDueDateResolver;
import com.red_escuela.backend.billing.invoice.service.InvoiceNumberGenerator;
import com.red_escuela.backend.billing.invoice.service.StudentSnapshotFinder;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

@Component
public class DraftInvoiceBatchApprover {
    private static final Logger log = LoggerFactory.getLogger(DraftInvoiceBatchApprover.class);

    private final InvoiceRepository invoiceRepository;
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceNumberGenerator numberGenerator;
    private final DraftInvoiceDueDateResolver dueDateResolver;
    private final StudentSnapshotFinder snapshotFinder;
    private final ApplicationEventPublisher eventPublisher;

    public DraftInvoiceBatchApprover(InvoiceRepository invoiceRepository, StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                     InvoiceNumberGenerator numberGenerator, DraftInvoiceDueDateResolver dueDateResolver, StudentSnapshotFinder snapshotFinder,
                                     ApplicationEventPublisher eventPublisher) {
        this.invoiceRepository = invoiceRepository;
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.numberGenerator = numberGenerator;
        this.dueDateResolver = dueDateResolver;
        this.snapshotFinder = snapshotFinder;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Invoice> approveDraftsOfRun(Long monthlyInvoiceRunId, Collection<Long> invoiceIds, LocalDate approvalDate, StatusChangeActor actor) {
        TreeSet<Long> orderedIds = new TreeSet<>(invoiceIds);
        Map<Long, StudentCurrentAccount> lockedAccounts = lockAccountsOfDrafts(orderedIds);
        List<Invoice> drafts = DraftInvoiceLocking.lockPendingDraftsOfRun(invoiceRepository, monthlyInvoiceRunId, orderedIds);
        drafts.forEach(draft -> approveDraft(draft, lockedAccounts.get(draft.getStudentCurrentAccountId()), approvalDate, actor));
        log.info("Borradores aprobados: corrida={}, cantidad={}, fecha={}", monthlyInvoiceRunId, drafts.size(), approvalDate);
        return drafts;
    }

    private Map<Long, StudentCurrentAccount> lockAccountsOfDrafts(TreeSet<Long> orderedIds) {
        TreeSet<Long> accountIds = new TreeSet<>();
        accountIds.addAll(invoiceRepository.findAccountIdsOfInvoices(orderedIds));
        Map<Long, StudentCurrentAccount> locked = new HashMap<>();
        accountIds.forEach(accountId -> locked.put(accountId, accountService.lockStudentCurrentAccount(accountId)));
        return locked;
    }

    private void approveDraft(Invoice draft, StudentCurrentAccount account, LocalDate approvalDate, StatusChangeActor actor) {
        draft.issueInvoice(numberGenerator.nextInvoiceNumber(InvoiceDocumentType.INVOICE), approvalDate, dueDateResolver.resolveDueDateOfDraftApprovedOn(draft, approvalDate),
                snapshotFinder.findSnapshotOfStudent(account.getStudentId()), actor);
        invoiceRepository.flush();
        accountService.recordInvoiceIssuedDebit(account, draft, approvalDate);
        allocationService.allocateAccountCreditToOldestInvoices(account, approvalDate, actor);
        RecordedInvoiceEventTranslator.translateRecordedEventsOf(draft).forEach(eventPublisher::publishEvent);
    }
}
