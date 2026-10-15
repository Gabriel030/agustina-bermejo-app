package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationCommand;
import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.events.AccountCreditAllocatedEvent;
import com.red_escuela.backend.billing.invoice.domain.event.InvoicePaidDomainEvent;
import com.red_escuela.backend.billing.operation.BillingOperation;
import com.red_escuela.backend.billing.operation.BillingOperationType;
import com.red_escuela.backend.shared.event.DomainEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccountCreditAllocationOperation implements BillingOperation<AccountCreditAllocationCommand, AccountCreditAllocationResult> {

    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;

    public AccountCreditAllocationOperation(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService) {
        this.accountService = accountService;
        this.allocationService = allocationService;
    }

    @Override
    public BillingOperationType type() {
        return BillingOperationType.ALLOCATE_ACCOUNT_CREDIT;
    }

    @Override
    public Class<AccountCreditAllocationCommand> contextType() {
        return AccountCreditAllocationCommand.class;
    }

    @Override
    public List<String> validationMoments() {
        return List.of("PAYMENT_ALLOCATION");
    }

    @Override
    public List<Class<? extends DomainEvent>> publishedEventTypes() {
        return List.of(AccountCreditAllocatedEvent.class, InvoicePaidDomainEvent.class);
    }

    @Override
    public void requireValidContext(AccountCreditAllocationCommand command) {
    }

    @Override
    public AccountCreditAllocationResult processOperation(AccountCreditAllocationCommand command) {
        StudentCurrentAccount account = accountService.lockStudentCurrentAccount(command.studentCurrentAccountId());
        return allocationService.allocateAccountCreditToOldestInvoices(account, command.businessDate(), command.actor());
    }

    @Override
    public List<? extends DomainEvent> domainEventsOf(AccountCreditAllocationCommand command, AccountCreditAllocationResult result) {
        return List.of();
    }
}
