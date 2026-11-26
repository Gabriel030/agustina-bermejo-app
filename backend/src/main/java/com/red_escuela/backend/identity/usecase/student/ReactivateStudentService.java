package com.red_escuela.backend.identity.usecase.student;

import com.red_escuela.backend.identity.service.StudentReactivator;
import com.red_escuela.backend.identity.specialist.StudentOnlyTargetPolicy;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class ReactivateStudentService implements ReactivateStudentUseCase {

    private final StudentReactivator studentReactivator;
    private final StudentOnlyTargetPolicy studentOnlyTargetPolicy;

    public ReactivateStudentService(StudentReactivator studentReactivator, StudentOnlyTargetPolicy studentOnlyTargetPolicy) {
        this.studentReactivator = studentReactivator;
        this.studentOnlyTargetPolicy = studentOnlyTargetPolicy;
    }

    @Override
    public void reactivate(ReactivateStudentCommand command) {
        studentOnlyTargetPolicy.requireStudentOnly(command.studentId());
        studentReactivator.reactivate(command.studentId(), command.request().reason());
    }
}
