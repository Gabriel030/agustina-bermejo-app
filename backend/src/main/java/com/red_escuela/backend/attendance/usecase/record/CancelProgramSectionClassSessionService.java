package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.specialist.CancelProgramSectionClassSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelProgramSectionClassSessionService implements CancelProgramSectionClassSessionUseCase {

    private final CancelProgramSectionClassSessionTransaction transaction;

    public CancelProgramSectionClassSessionService(CancelProgramSectionClassSessionTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void cancel(CancelProgramSectionClassSessionCommand command) {
        transaction.cancel(command.sectionId(), command.instructorId(), command.classDate());
    }
}
