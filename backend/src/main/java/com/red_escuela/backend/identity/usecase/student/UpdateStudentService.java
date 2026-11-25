package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.dto.StudentRecordResponse;
import com.red_escuela.backend.identity.service.UpdateStudentTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class UpdateStudentService implements UpdateStudentUseCase {
    private final UpdateStudentTransaction updateStudentTransaction;

    public UpdateStudentService(UpdateStudentTransaction updateStudentTransaction) {
        this.updateStudentTransaction = updateStudentTransaction;
    }

    @Override
    public StudentRecordResponse update(UpdateStudentCommand command) {
        return updateStudentTransaction.update(command.studentId(), command.request(), command.includeWithdrawalReason());
    }
}
