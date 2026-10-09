package com.red_escuela.backend.billing.account.controller;

import com.red_escuela.backend.billing.account.dto.AccountCreditAllocationResponse;
import com.red_escuela.backend.billing.account.usecase.AllocateAccountCreditUseCase;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrentAccountAllocationController {
    private final AllocateAccountCreditUseCase allocateAccountCreditUseCase;

    public CurrentAccountAllocationController(AllocateAccountCreditUseCase allocateAccountCreditUseCase) {
        this.allocateAccountCreditUseCase = allocateAccountCreditUseCase;
    }

    @PostMapping("/students/{studentId}/current-account/allocations")
    public AccountCreditAllocationResponse allocateCredit(@PathVariable Long studentId) {
        return AccountCreditAllocationResponse.from(allocateAccountCreditUseCase.allocateCreditOfStudentAccounts(studentId));
    }
}
