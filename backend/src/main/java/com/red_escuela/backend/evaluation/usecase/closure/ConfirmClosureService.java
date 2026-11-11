package com.red_escuela.backend.evaluation.usecase.closure;

import com.red_escuela.backend.evaluation.specialist.CommissionClosureTeacherConfirmer;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ConfirmClosureService implements ConfirmClosureUseCase {

    private final CommissionClosureTeacherConfirmer teacherConfirmer;

    public ConfirmClosureService(CommissionClosureTeacherConfirmer teacherConfirmer) {
        this.teacherConfirmer = teacherConfirmer;
    }

    @Override
    public void confirm(ConfirmClosureCommand command) {
        teacherConfirmer.confirmClosure(command.kind(), command.sectionId(), command.instructorId());
    }
}
