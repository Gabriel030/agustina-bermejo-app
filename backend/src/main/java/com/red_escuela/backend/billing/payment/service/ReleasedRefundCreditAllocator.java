package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.invoice.service.InvoiceAllocationQueryService;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

@Component
public class ReleasedRefundCreditAllocator {
    private static final Logger log = LoggerFactory.getLogger(ReleasedRefundCreditAllocator.class);

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;
    private final InvoiceAllocationQueryService invoiceQueries;
    private final TransactionTemplate independentTransaction;
    private final Clock clock;

    public ReleasedRefundCreditAllocator(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService,
                                         InvoiceAllocationQueryService invoiceQueries, PlatformTransactionManager transactionManager, Clock clock) {
        this.accountService = accountService;
        this.allocationService = allocationService;
        this.invoiceQueries = invoiceQueries;
        this.independentTransaction = new TransactionTemplate(transactionManager);
        this.independentTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    public void allocateAfterCommit(Collection<Long> accountIds, StatusChangeActor actor) {
        List<Long> ascendingAccountIds = accountIds.stream().distinct().sorted().toList();
        if (ascendingAccountIds.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            allocateWithoutBlocking(ascendingAccountIds, actor);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                allocateWithoutBlocking(ascendingAccountIds, actor);
            }
        });
    }

    private void allocateWithoutBlocking(List<Long> accountIds, StatusChangeActor actor) {
        for (Long accountId : accountIds) {
            try {
                independentTransaction.executeWithoutResult(status -> allocateReleasedCreditOf(accountId, actor));
            } catch (RuntimeException failure) {
                log.error("No se pudo imputar el saldo liberado por una devolución cerrada en la cuenta {}; el cierre queda registrado y el saldo lo toma la próxima imputación",
                        accountId, failure);
            }
        }
    }

    private void allocateReleasedCreditOf(Long accountId, StatusChangeActor actor) {
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(accountId);
        invoiceQueries.lockOpenInvoicesOfAccountInIdOrder(accountId);
        int allocations = allocationService.allocateAccountCreditToOldestInvoices(account, LocalDate.now(clock), actor).allocations().size();
        log.info("Saldo liberado por una devolución cerrada imputado: cuenta={}, imputaciones={}", accountId, allocations);
    }
}
