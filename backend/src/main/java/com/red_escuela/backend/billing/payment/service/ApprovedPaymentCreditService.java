package com.red_escuela.backend.billing.payment.service;

import com.red_escuela.backend.billing.account.domain.AccountCreditAllocationResult;
import com.red_escuela.backend.billing.account.domain.StudentCurrentAccount;
import com.red_escuela.backend.billing.account.service.CurrentAccountAllocationService;
import com.red_escuela.backend.billing.account.service.StudentCurrentAccountService;
import com.red_escuela.backend.billing.payment.domain.ApprovedPaymentCreditingResult;
import com.red_escuela.backend.billing.payment.domain.Payment;
import com.red_escuela.backend.billing.payment.domain.PaymentStatus;
import com.red_escuela.backend.shared.domain.Money;
import com.red_escuela.backend.shared.domain.statemachine.StatusChangeActor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Set;

@Service
public class ApprovedPaymentCreditService {
    private final StudentCurrentAccountService accountService;
    private final CurrentAccountAllocationService allocationService;

    public ApprovedPaymentCreditService(StudentCurrentAccountService accountService, CurrentAccountAllocationService allocationService) {
        this.accountService = accountService;
        this.allocationService = allocationService;
    }

    public ApprovedPaymentCreditingResult creditApprovedPaymentAndAllocate(StudentCurrentAccount lockedAccount, Payment approvedPayment, LocalDate today,
                                                                          StatusChangeActor actor, Set<Long> programAdmissionInvoiceIds) {
        if (approvedPayment.getStatus() != PaymentStatus.APPROVED || !approvedPayment.getStudentCurrentAccountId().equals(lockedAccount.getId())) {
            throw new IllegalArgumentException("Only an approved payment of the locked account can be credited");
        }
        Money received = approvedPayment.getApprovedAmount().value();
        accountService.recordPaymentReceivedCredit(lockedAccount, approvedPayment.getId(), approvedPayment.getApprovedAmount(), approvedPayment.getPaidOn(),
                "Pago " + approvedPayment.getId());
        AccountCreditAllocationResult allocation = allocationService.allocateAccountCreditSettlingProgramAdmissionInvoicesFirst(lockedAccount, today, actor,
                programAdmissionInvoiceIds);
        Money unallocatedAfterwards = lockedAccount.getUnallocatedCreditAmount().value();
        Money leftAsCredit = unallocatedAfterwards.isGreaterThan(received) ? received : unallocatedAfterwards;
        return new ApprovedPaymentCreditingResult(allocation, leftAsCredit);
    }
}
