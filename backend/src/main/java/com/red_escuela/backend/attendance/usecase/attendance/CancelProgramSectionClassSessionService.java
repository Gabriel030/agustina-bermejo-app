package com.red_escuela.backend.attendance.usecase.attendance;

import com.red_escuela.backend.attendance.service.ClassSessionCanceller;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CancelProgramSectionClassSessionService implements CancelProgramSectionClassSessionUseCase {

    private final ClassSessionCanceller classSessionCanceller;

    public CancelProgramSectionClassSessionService(ClassSessionCanceller classSessionCanceller) {
        this.classSessionCanceller = classSessionCanceller;
    }

    @Override
    @Transactional
    public void cancel(CancelProgramSectionClassSessionCommand command) {
        classSessionCanceller.cancelProgramSectionSession(command.sectionId(), command.instructorId(), command.classDate());
    }
}
