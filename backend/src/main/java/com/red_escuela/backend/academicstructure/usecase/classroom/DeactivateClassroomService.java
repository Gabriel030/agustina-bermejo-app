package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.exception.ClassroomNotFoundException;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.entity.StatusType;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class DeactivateClassroomService implements DeactivateClassroomUseCase {

    private final ClassroomRepository classroomRepository;

    public DeactivateClassroomService(ClassroomRepository classroomRepository) {
        this.classroomRepository = classroomRepository;
    }

    @Override
    public void deactivate(Long id) {
        var classroom = classroomRepository.findById(id)
                .orElseThrow(() -> new ClassroomNotFoundException(id));
        classroom.setStatus(StatusType.INACTIVE);
        classroomRepository.save(classroom);
    }
}
