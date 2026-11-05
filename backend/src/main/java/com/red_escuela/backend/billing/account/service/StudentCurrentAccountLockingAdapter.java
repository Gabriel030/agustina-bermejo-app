package com.red_escuela.backend.billing.account.service;

import com.red_escuela.backend.shared.domain.AccountLockingPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StudentCurrentAccountLockingAdapter implements AccountLockingPort {
    private final StudentCurrentAccountService accountService;

    public StudentCurrentAccountLockingAdapter(StudentCurrentAccountService accountService) {
        this.accountService = accountService;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockAccountById(Long accountId) {
        accountService.lockStudentCurrentAccount(accountId);
    }
}
