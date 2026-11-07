package com.red_escuela.backend.attendance.usecase.record;

import com.red_escuela.backend.attendance.specialist.ClassSessionCanceller;
import com.red_escuela.backend.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class CancelCourseSectionClassSessionService implements CancelCourseSectionClassSessionUseCase {

    private final ClassSessionCanceller classSessionCanceller;

    public CancelCourseSectionClassSessionService(ClassSessionCanceller classSessionCanceller) {
        this.classSessionCanceller = classSessionCanceller;
    }

    @Override
    @Transactional
    public void cancel(CancelCourseSectionClassSessionCommand command) {
        classSessionCanceller.cancelCourseSectionSession(command.sectionId(), command.instructorId(), command.classDate());
    }
}
