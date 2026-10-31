package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.service.ClassroomDeactivator;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateClassroomService implements DeactivateClassroomUseCase {

    private final ClassroomDeactivator deactivator;

    public DeactivateClassroomService(ClassroomDeactivator deactivator) {
        this.deactivator = deactivator;
    }

    @Override
    public void deactivate(Long id) {
        deactivator.deactivateClassroom(id);
    }
}
