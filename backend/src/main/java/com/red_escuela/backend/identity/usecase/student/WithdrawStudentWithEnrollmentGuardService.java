package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.domain.StudentWithdrawalGuard;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@Primary
public class WithdrawStudentWithEnrollmentGuardService implements WithdrawStudentUseCase {

    private final StudentWithdrawalGuard studentWithdrawalGuard;
    private final WithdrawStudentService withdrawStudentService;

    public WithdrawStudentWithEnrollmentGuardService(StudentWithdrawalGuard studentWithdrawalGuard, WithdrawStudentService withdrawStudentService) {
        this.studentWithdrawalGuard = studentWithdrawalGuard;
        this.withdrawStudentService = withdrawStudentService;
    }

    @Override
    @Transactional
    public void withdraw(WithdrawStudentCommand command) {
        studentWithdrawalGuard.lockStudentAndRequireWithdrawable(command.studentId());
        withdrawStudentService.withdraw(command);
    }
}
