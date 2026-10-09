package com.red_escuela.backend.billing.account.usecase;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;

import java.util.List;

public interface AllocateAccountCreditUseCase {
    List<AccountCreditAllocationResult> allocateCreditOfStudentAccounts(Long studentId);
}
