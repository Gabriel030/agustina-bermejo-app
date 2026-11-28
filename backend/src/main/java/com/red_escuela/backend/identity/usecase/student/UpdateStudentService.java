package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.service.UpdateStudentTransaction;
import com.red_escuela.backend.identity.specialist.StudentOnlyTargetPolicy;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateStudentService implements UpdateStudentUseCase {
    private final UpdateStudentTransaction updateStudentTransaction;
    private final StudentOnlyTargetPolicy studentOnlyTargetPolicy;

    public UpdateStudentService(UpdateStudentTransaction updateStudentTransaction, StudentOnlyTargetPolicy studentOnlyTargetPolicy) {
        this.updateStudentTransaction = updateStudentTransaction;
        this.studentOnlyTargetPolicy = studentOnlyTargetPolicy;
    }

    @Override
    public StudentRecordResponse update(UpdateStudentCommand command) {
        studentOnlyTargetPolicy.requireStudentOnly(command.studentId());
        return updateStudentTransaction.update(command.studentId(), command.request(), command.includeWithdrawalReason());
    }
}
