package com.red_escuela.backend.academicstructure.usecase.classroom;

import com.red_escuela.backend.academicstructure.dto.ClassroomResponse;
import com.red_escuela.backend.academicstructure.dto.CreateClassroomRequest;
import com.red_escuela.backend.academicstructure.entity.Classroom;
import com.red_escuela.backend.academicstructure.repository.ClassroomRepository;
import com.red_escuela.backend.shared.UseCase;

@UseCase
public class CreateClassroomService implements CreateClassroomUseCase {

    private final ClassroomRepository classroomRepository;

    public CreateClassroomService(ClassroomRepository classroomRepository) {
        this.classroomRepository = classroomRepository;
    }

    @Override
    public ClassroomResponse create(CreateClassroomRequest request) {
        var classroom = new Classroom();
        classroom.setName(request.name());
        classroom.setCapacity(request.capacity());
        return ClassroomResponse.from(classroomRepository.save(classroom));
    }
}
