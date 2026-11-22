package com.red_escuela.backend.identity.service;

import com.red_escuela.backend.identity.domain.StudentStatusChangeRecorder;
import com.red_escuela.backend.identity.domain.StudentWithdrawalGuard;
import com.red_escuela.backend.identity.dto.WithdrawStudentRequest;
import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WithdrawStudentWithGuardTransaction {
    private final StudentWithdrawalGuard studentWithdrawalGuard;
    private final WithdrawStudentTransaction withdrawStudentTransaction;
    private final StudentStatusChangeRecorder studentStatusChangeRecorder;
    private final IdentityAuditRecorder identityAuditRecorder;

    public WithdrawStudentWithGuardTransaction(StudentWithdrawalGuard studentWithdrawalGuard,
                                               WithdrawStudentTransaction withdrawStudentTransaction,
                                               StudentStatusChangeRecorder studentStatusChangeRecorder,
                                               IdentityAuditRecorder identityAuditRecorder) {
        this.studentWithdrawalGuard = studentWithdrawalGuard;
        this.withdrawStudentTransaction = withdrawStudentTransaction;
        this.studentStatusChangeRecorder = studentStatusChangeRecorder;
        this.identityAuditRecorder = identityAuditRecorder;
    }

    @Transactional
    public void withdraw(Long studentId, WithdrawStudentRequest request) {
        studentWithdrawalGuard.lockStudentAndRequireWithdrawable(studentId);
        withdrawStudentTransaction.withdraw(studentId, request);
        studentStatusChangeRecorder.recordWithdrawal(studentId);
        identityAuditRecorder.recordStudentWithdrawn(studentId, request);
    }
}
