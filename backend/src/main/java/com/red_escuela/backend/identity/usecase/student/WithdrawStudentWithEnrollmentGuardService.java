package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.specialist.IdentityAuditRecorder;
import com.red_escuela.backend.identity.domain.StudentStatusChangeRecorder;
import com.red_escuela.backend.identity.domain.StudentWithdrawalGuard;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@Primary
public class WithdrawStudentWithEnrollmentGuardService implements WithdrawStudentUseCase {

    private final StudentWithdrawalGuard studentWithdrawalGuard;
    private final WithdrawStudentService withdrawStudentService;

    private final StudentStatusChangeRecorder studentStatusChangeRecorder;
    private final IdentityAuditRecorder identityAuditRecorder;

    public WithdrawStudentWithEnrollmentGuardService(StudentWithdrawalGuard studentWithdrawalGuard, WithdrawStudentService withdrawStudentService,
            StudentStatusChangeRecorder studentStatusChangeRecorder, IdentityAuditRecorder identityAuditRecorder) {
        this.identityAuditRecorder = identityAuditRecorder;
        this.studentStatusChangeRecorder = studentStatusChangeRecorder;
        this.studentWithdrawalGuard = studentWithdrawalGuard;
        this.withdrawStudentService = withdrawStudentService;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        studentWithdrawalGuard.lockStudentAndRequireWithdrawable(command.studentId());
        withdrawStudentService.withdraw(command);
        studentStatusChangeRecorder.recordWithdrawal(command.studentId());
        identityAuditRecorder.recordStudentWithdrawn(command.studentId(), command.request());
    }
}
