package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.specialist.CancelCourseSectionClassSessionTransaction;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CancelCourseSectionClassSessionService implements CancelCourseSectionClassSessionUseCase {

    private final CancelCourseSectionClassSessionTransaction transaction;

    public CancelCourseSectionClassSessionService(CancelCourseSectionClassSessionTransaction transaction) {
        this.transaction = transaction;
    }

    @Override
    public void cancel(CancelCourseSectionClassSessionCommand command) {
        transaction.cancel(command.sectionId(), command.instructorId(), command.classDate());
    }
}
