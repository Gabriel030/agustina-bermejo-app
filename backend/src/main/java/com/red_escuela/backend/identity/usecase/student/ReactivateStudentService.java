package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.service.StudentReactivator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReactivateStudentService implements ReactivateStudentUseCase {

    private final StudentReactivator studentReactivator;

    public ReactivateStudentService(StudentReactivator studentReactivator) {
        this.studentReactivator = studentReactivator;
    }

    @Override
    public void reactivate(ReactivateStudentCommand command) {
        studentReactivator.reactivate(command.studentId(), command.request().reason());
    }
}
